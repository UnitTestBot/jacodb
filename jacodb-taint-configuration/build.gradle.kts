plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    testImplementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")

    api(group = "com.google.code.gson", name = "gson", version = "2.10.1")
}

tasks.test {
    useJUnitPlatform()
}