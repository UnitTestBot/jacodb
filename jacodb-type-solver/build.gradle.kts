repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // core
//    implementation("com.github.UnitTestBot.klogic:klogic-core:0.1.4")
    // util terms
//    implementation("com.github.UnitTestBot.klogic:klogic-utils:0.1.4")

    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    testImplementation("com.google.code.gson:gson:2.8.9")
}
