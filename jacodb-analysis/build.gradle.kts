val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

plugins {
    `java-test-fixtures`
    kotlin("plugin.serialization") version "1.7.20"
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(project(":jacodb-api"))
    testImplementation(group = "javax.servlet", name = "servlet-api", version = "2.5")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = "5.9.2")
    testImplementation(files("src/test/resources/juliet.jar"))
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation(group = "joda-time", name = "joda-time", version = "2.12.5")

    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.4.1")
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}