plugins {
    id("com.android.application")
}

android {
    namespace = "edu.dlsu.driverguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.dlsu.driverguard.uidemo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-ui-demo"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
