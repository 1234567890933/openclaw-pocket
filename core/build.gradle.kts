plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * The non-UI half of the app: gateway, runtime, tools, bots, storage, security.
 *
 * Split out for two reasons. It has no Compose dependency, so it compiles in a
 * fraction of the time — which matters on a small machine, and it is why this
 * project can be built at all on 2 GB. And the boundary is real: nothing here may
 * import a Compose type, so the agent layer cannot quietly grow a UI dependency.
 */
android {
    namespace = "com.openclaude.mobile.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        resourceConfigurations += listOf("en", "hi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)


    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
