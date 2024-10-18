plugins {
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jacodb-api-jvm"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json) // for local tests only

    testImplementation(testFixtures(project(":jacodb-storage")))
    testImplementation(Libs.kotlin_logging)
}

tasks.test {
    useJUnitPlatform()
}
