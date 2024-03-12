plugins {
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api-common"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_serialization_json)
    testImplementation(Libs.mockk)

    testImplementation(kotlin("test"))
    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
}

tasks.test {
    useJUnitPlatform()
}