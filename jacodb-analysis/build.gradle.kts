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
    testFixturesImplementation("javax.servlet:servlet-api:2.5")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation(files("src/testFixtures/resources/juliet.jar"))
    testImplementation(files("src/testFixtures/resources/pointerbench.jar"))

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
}