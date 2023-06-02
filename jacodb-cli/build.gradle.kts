val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-analysis"))
    api(project(":jacodb-api"))

    testImplementation(testFixtures(project(":jacodb-core")))
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.4.1")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-cli", version = "0.3.5")
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}