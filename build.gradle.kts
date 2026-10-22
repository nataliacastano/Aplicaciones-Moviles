// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

// Salida local opcional: evita bloqueos de archivos generados en carpetas sincronizadas.
// local.properties no se comparte; sin esta propiedad se mantiene la ruta build habitual.
val localBuildSettings = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
localBuildSettings.getProperty("build.outputRoot")?.takeIf { it.isNotBlank() }?.let { location ->
    val outputRoot = rootProject.file(location)
    layout.buildDirectory.set(outputRoot.resolve("root"))
    subprojects { layout.buildDirectory.set(outputRoot.resolve(name)) }
}
