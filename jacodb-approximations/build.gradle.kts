dependencies {
    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    testImplementation(Libs.kotlin_logging)
    testRuntimeOnly(Libs.guava)
}
