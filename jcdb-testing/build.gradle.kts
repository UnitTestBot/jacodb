val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

dependencies {
    api(project(":jcdb-api"))

    api(platform("org.junit:junit-bom:5.8.2"))
    api(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    api(group = "org.junit.jupiter", name = "junit-jupiter")
    api(group = "com.google.guava", name = "guava", version = "31.1-jre")
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    implementation("org.mockito:mockito-core:4.2.0")

}