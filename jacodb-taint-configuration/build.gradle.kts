plugins {
    id("java")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}

tasks.test {
    useJUnitPlatform()
}