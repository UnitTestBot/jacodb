val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject


dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    implementation("net.lingala.zip4j:zip4j:2.11.3")

    testImplementation(testFixtures(project(":jacodb-core")))
}