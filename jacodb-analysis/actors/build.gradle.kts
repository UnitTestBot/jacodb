dependencies {
    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(kotlin("test"))
    testImplementation(Libs.mockk)
}
