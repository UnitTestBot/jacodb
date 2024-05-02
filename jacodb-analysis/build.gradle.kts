plugins {
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    api(project(":jacodb-api-jvm"))
    api(project(":jacodb-core"))
    api(project(":jacodb-taint-configuration"))
    api(project(":jacodb-ets"))
    api(project(":jacodb-panda-static"))
    api(project(":jacodb-go"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    api(Libs.sarif4k)

    testImplementation(project(":jacodb-api-jvm"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(kotlin("test"))
    testImplementation(Libs.mockk)

    // Additional deps for analysis:
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation(Libs.joda_time)
    testImplementation(Libs.juliet_support)
    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation(Libs.juliet_cwe(cweNum))
    }
}
