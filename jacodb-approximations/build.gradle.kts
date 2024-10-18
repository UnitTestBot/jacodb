dependencies {
    implementation(project(":jacodb-api-jvm"))
    implementation(project(":jacodb-core"))

    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(testFixtures(project(":jacodb-storage")))
    testImplementation(Libs.kotlin_logging)
    testRuntimeOnly(Libs.guava)
}
