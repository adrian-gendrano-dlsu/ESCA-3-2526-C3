plugins {
    id("com.android.application")
}

android {
    namespace = "edu.dlsu.esca_3_2526_c3"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.dlsu.esca_3_2526_c3.esca_3_2526_c3"
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
