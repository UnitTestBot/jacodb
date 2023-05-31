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
}

tasks.test {
    useJUnitPlatform()
}