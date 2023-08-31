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
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = "5.9.2")
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation(group = "joda-time", name = "joda-time", version = "2.12.5")
    testImplementation(group = "com.github.UnitTestBot.juliet-java-test-suite", name = "support", version = "1.3.1")
    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation(group = "com.github.UnitTestBot.juliet-java-test-suite", name = "cwe${cweNum}", version = "1.3.1")
    }

    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.4.1")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
    implementation(group =  "org.slf4j", name = "slf4j-simple", version = "1.6.1")
}
