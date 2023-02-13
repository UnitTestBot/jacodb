val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject


dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    testImplementation(testFixtures(project(":jacodb-core")))
}