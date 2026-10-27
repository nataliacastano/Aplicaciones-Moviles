plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
}

// Permite compilar antes de descargar la configuración real del proyecto del equipo.
val authEmulatorHost = providers.gradleProperty("firebaseAuthEmulatorHost").orElse("").get()
// Las pruebas de UI usan otra aplicación para que su desinstalación no borre datos de desarrollo.
val homeUiTests = providers.gradleProperty("homeUiTests").orElse("false").get().toBoolean()
if (file("google-services.json").isFile && authEmulatorHost.isBlank() && !homeUiTests) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.example.gestorempresa"
    compileSdk = 36

    defaultConfig {
        applicationId = if (homeUiTests) "com.example.gestorempresa.uitests" else "com.example.gestorempresa"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "AUTH_EMULATOR_HOST", "\"\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val host = authEmulatorHost
            require(host.matches(Regex("[A-Za-z0-9.:-]*"))) { "Host de pruebas no válido" }
            buildConfigField("String", "AUTH_EMULATOR_HOST", "\"$host\"")
            if (host.isNotBlank()) {
                applicationIdSuffix = ".pruebas"
                versionNameSuffix = "-pruebas"
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").resources.srcDir("$projectDir/schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        doFirst {
            check(homeUiTests) {
                "Ejecuta las pruebas con -PhomeUiTests=true para proteger los datos de la app de desarrollo."
            }
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
