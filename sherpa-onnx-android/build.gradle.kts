plugins {
    base
    id("maven-publish")
}

configurations.maybeCreate("default")
val aarFile = file("sherpa-onnx-android-1.12.25.aar")
artifacts.add("default", aarFile)

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.k2fsa.sherpa.onnx"
            artifactId = "sherpa-onnx-android"
            version = "1.12.25"
            artifact(aarFile)
        }
    }
}
