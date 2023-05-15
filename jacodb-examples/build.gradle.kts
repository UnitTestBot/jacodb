val coroutinesVersion: String by rootProject

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))
    api(project(":jacodb-analysis"))

    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-reactor", version = coroutinesVersion)

}