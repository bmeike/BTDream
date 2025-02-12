// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

extra.apply {
    set("COUCHBASE_LITE_VERSION", "4.0.0-SNAPSHOT")
}
