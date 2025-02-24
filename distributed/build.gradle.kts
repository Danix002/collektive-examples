plugins {
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.bundles.collektive)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.hivemq)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.slf4j)
}