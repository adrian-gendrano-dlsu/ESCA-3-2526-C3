plugins {
    id("com.android.application")
}

android {
    namespace = "edu.dlsu.driverguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.dlsu.driverguard.facetrackingdemo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-face-demo"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.13.0")

    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")


}
