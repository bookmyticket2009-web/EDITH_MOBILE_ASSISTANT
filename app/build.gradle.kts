
plugins {
    id("com.android.application")
}

android {
    namespace = "com.pocketai.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    packaging {

        jniLibs {

            useLegacyPackaging = true

        }

    }


    defaultConfig {
        applicationId = "com.pocketai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
