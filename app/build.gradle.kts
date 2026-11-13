plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.openclaude.mobile"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        // Distribution identity. The Kotlin namespace stays com.openclaude.mobile so
        // the JNI symbols and every import keep working; only what the phone shows
        // changes.
        applicationId = "com.pocketclaw.app"
        minSdk = 26            // proot needs API 26+ for exec of bundled binaries
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Ship English and Hindi resources only; every other locale is dead weight.
        resourceConfigurations += listOf("en", "hi")

        // One APK per CPU architecture. A 32-bit phone cannot install an arm64 build at
        // all, so a single universal APK silently excludes them; three focused APKs
        // let each device download only what it can actually run.
        //
        // The bundled Linux userland, Node runtime and proot are aarch64-only. On the
        // 32-bit and x86 builds the chat still works through the direct provider path
        // (plain HTTPS from Kotlin); only the on-device gateway is unavailable, and the
        // app says so rather than pretending.
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86_64")
                isUniversalApk = false
            }
        }

        ndk {
            // The bundled Linux userland is aarch64 only.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-O2", "-ffunction-sections", "-fdata-sections")
                // -PocmLocalLlm=OFF builds the stub engine. Needed on machines where the
                // NDK build of ggml is the only thing standing between you and an APK.
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DOCM_ENABLE_LOCAL_LLM=${project.findProperty("ocmLocalLlm") ?: "ON"}"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        // No secrets in the repository. If a real keystore is supplied through the
        // environment it is used; otherwise the release build is signed with the debug
        // key so the artefact is installable, which is what a preview build needs.
        create("ci") {
            val storePath = System.getenv("OCM_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore"
            storeFile = file(storePath)
            storePassword = System.getenv("OCM_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("OCM_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("OCM_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        }
        // Bundled runtime archives are downloaded at first boot, not packed in the APK.
        jniLibs { useLegacyPackaging = false }
    }
}

// Compose compiler: drop the metadata and source-info passes. Both are debug
// conveniences (live literals, source positions in traces); removing them cuts
// compile time on small machines and a few hundred KB from the APK.
composeCompiler {
    generateFunctionKeyMetaClasses = false
    includeSourceInformation = false
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)





    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)



    testImplementation(project(":core"))
    testImplementation(libs.junit)
}
