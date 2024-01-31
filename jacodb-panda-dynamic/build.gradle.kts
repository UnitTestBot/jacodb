plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.jacodb"
version = "1.4-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api-jvm"))
    api(project(":jacodb-api-core"))

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    implementation(Libs.kotlin_logging)
    implementation(Libs.gson)
    implementation(Libs.kotlinx_serialization_json)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}