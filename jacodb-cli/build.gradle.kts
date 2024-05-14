plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-analysis:taint"))
    api(project(":jacodb-analysis:ifds"))
    api(project(":jacodb-api"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_cli)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(testFixtures(project(":jacodb-core")))
}
