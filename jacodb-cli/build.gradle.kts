dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-analysis"))
    api(project(":jacodb-api-jvm"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_cli)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(testFixtures(project(":jacodb-core")))
}
