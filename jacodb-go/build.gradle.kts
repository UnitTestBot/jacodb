plugins {
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-core"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.jdot)

    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}