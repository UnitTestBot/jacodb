val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject


dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))

    implementation("net.lingala.zip4j:zip4j:2.11.3")
    implementation("io.github.microutils:kotlin-logging:1.8.3")
    testImplementation(testFixtures(project(":jacodb-core")))

}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.jacodb.analysis.codegen.GeneratorKt"
    }
    from(configurations.runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) })
}