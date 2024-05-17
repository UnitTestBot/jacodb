dependencies {
    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)

    api(project(":jacodb-analysis:actors"))
    api(project(":jacodb-analysis:common"))

    testImplementation(kotlin("test"))
}
