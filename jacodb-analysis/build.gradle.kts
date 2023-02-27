val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    testImplementation(testFixtures(project(":jacodb-core")))
    testFixturesImplementation(project(":jacodb-api"))
}