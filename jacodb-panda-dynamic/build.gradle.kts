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

    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

tasks {

    compileKotlin {
        dependsOn(generateGrammarSource)
    }

    compileTestKotlin {
        dependsOn(generateTestGrammarSource)
    }
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.file("generated-src/antlr/main/java"))
        }
    }
}

tasks {
    license {
        exclude("**/antlr/")
    }
}
