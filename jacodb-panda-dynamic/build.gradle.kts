plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-core"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(kotlin("test"))
    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}
