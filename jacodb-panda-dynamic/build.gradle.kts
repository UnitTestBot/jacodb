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
    antlr("org.antlr:antlr4:4.13.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

sourceSets.getByName("main").java {
    srcDir("build/generated-src/antlr/main/java")
}

tasks {
    generateGrammarSource {

        arguments.addAll(listOf("-visitor", "-package", "antlr"))
        outputDirectory = File("${project.projectDir}/build/generated-src/antlr/main/java/antlr")
    }

    compileKotlin {
        dependsOn(generateGrammarSource)
    }
}
