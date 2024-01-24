dependencies {
    implementation(project(":jacodb-api-core"))
    implementation(project(":jacodb-api-jvm"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    testImplementation(Libs.kotlin_logging)
    testRuntimeOnly(Libs.guava)
}
