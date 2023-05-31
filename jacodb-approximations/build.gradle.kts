plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
}

tasks.test {
    useJUnitPlatform()
}