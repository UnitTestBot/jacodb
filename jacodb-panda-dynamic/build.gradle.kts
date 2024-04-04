plugins {
    kotlin("plugin.serialization")
    antlr
}

dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-core"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.jdot)

    antlr(Libs.antlr)

    testImplementation(kotlin("test"))
    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

tasks {
    generateGrammarSource {
        maxHeapSize = "64m"
        arguments.addAll(listOf("-visitor", "-package", "antlr"))
        outputDirectory = layout.buildDirectory.file("antlr/main/java/antlr").get().asFile
    }

    compileKotlin {
        dependsOn(generateGrammarSource)
    }
}

tasks {
    license {
        exclude("**/antlr/")
    }
}
