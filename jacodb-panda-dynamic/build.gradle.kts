plugins {
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api-jvm"))
    api(project(":jacodb-api-core"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
}

tasks.test {
    useJUnitPlatform()
}