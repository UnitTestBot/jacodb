plugins {
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

val testSamplesConfig by configurations.creating
val testSamples by sourceSets.creating{
    compileClasspath += testSamplesConfig
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.sarif4k)

    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(project(":jacodb-api"))
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation(Libs.joda_time)
    testImplementation(Libs.juliet_support)

    testSamplesConfig(Libs.juliet_support)

    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation(Libs.juliet_cwe(cweNum))
    }
}

sourceSets["test"].compileClasspath += testSamples.output
