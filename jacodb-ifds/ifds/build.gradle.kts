dependencies {
    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)

    implementation(project(":jacodb-ifds:actors"))

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
