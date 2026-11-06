plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied: the Android plugin compiles Kotlin itself. Listing it here
    // pins the Kotlin compiler to the version in libs.versions.toml.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
