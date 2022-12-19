val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

dependencies {
    api(project(":jcdb-api"))

    api(platform("org.junit:junit-bom:5.9.0"))
    api(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    api(group = "org.junit.jupiter", name = "junit-jupiter")
    api(group = "com.google.guava", name = "guava", version = "31.1-jre")
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    implementation(group = "org.mockito", name = "mockito-core", version = "4.8.0")
    //implementation(group = "org.jetbrains", name = "annotations", version = "20.1.0")
}