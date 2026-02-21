package com.xeles.offlinevoiceai.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility to extract model assets from the app's `assets/` folder
 * to the device's internal storage (`filesDir`).
 *
 * Vosk and Sherpa-ONNX require absolute file paths to load C++ models —
 * they cannot read from Android's compressed asset streams directly.
 */
object AssetExtractor {

    private const val TAG = "AssetExtractor"

    // Bump this version whenever asset structure changes to force re-extraction
    private const val EXTRACTION_VERSION = 2
    private const val MARKER_FILE = ".extracted_v"

    /**
     * Extracts an asset folder to [context.filesDir]/[assetName].
     *
     * If the folder has already been extracted with the current version,
     * the extraction is skipped. Otherwise, old data is cleared and
     * a fresh extraction is performed.
     *
     * @param context   Application context.
     * @param assetName The name of the folder inside `assets/` to extract.
     * @return The absolute path to the extracted folder on disk, or `null` on failure.
     */
    suspend fun extract(context: Context, assetName: String): String? = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, assetName)
        val markerFile = File(targetDir, "$MARKER_FILE$EXTRACTION_VERSION")

        // Skip if already extracted with the current version
        if (markerFile.exists()) {
            Log.d(TAG, "Asset '$assetName' already extracted (v$EXTRACTION_VERSION) at: ${targetDir.absolutePath}")
            return@withContext targetDir.absolutePath
        }

        // Delete old extraction (if any) to start fresh
        if (targetDir.exists()) {
            Log.d(TAG, "Deleting stale extraction of '$assetName'")
            targetDir.deleteRecursively()
        }

        try {
            Log.d(TAG, "Extracting asset '$assetName' to: ${targetDir.absolutePath}")
            copyAssetFolder(context, assetName, targetDir)

            // Log the extracted contents for debugging
            val files = targetDir.listFiles()?.map { it.name } ?: emptyList()
            Log.d(TAG, "Extracted contents of '$assetName': $files")

            // Write versioned marker file to signal successful extraction
            markerFile.createNewFile()

            Log.d(TAG, "Asset '$assetName' extraction complete.")
            targetDir.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract asset '$assetName'", e)
            // Clean up partial extraction
            targetDir.deleteRecursively()
            null
        }
    }

    /**
     * Recursively copies an asset folder (or file) to the target directory.
     */
    private fun copyAssetFolder(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath)

        if (children.isNullOrEmpty()) {
            // It's a file — copy it
            copyAssetFile(context, assetPath, targetDir)
        } else {
            // It's a directory — recurse
            targetDir.mkdirs()
            for (child in children) {
                copyAssetFolder(
                    context,
                    "$assetPath/$child",
                    File(targetDir, child)
                )
            }
        }
    }

    /**
     * Copies a single asset file to the given target [File].
     */
    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "  Copied: $assetPath (${targetFile.length()} bytes)")
    }
}
