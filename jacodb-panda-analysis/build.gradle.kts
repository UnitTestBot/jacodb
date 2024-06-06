plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":jacodb-analysis"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.jdot)

//    testImplementation(project(":jacodb-analysis"))
//    testImplementation(testFixtures(project(":jacodb-core")))
//    testImplementation(Libs.mockk)
}