import java.io.FileNotFoundException

plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-core"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.jdot)

    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

tasks.register("generateTestResources") {
    doLast {
        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = rootDir.resolve("../arkanalyzer").path

        val arkAnalyzerDir = System.getenv(envVarName) ?: run {
            println("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
            defaultArkAnalyzerDir
        }
        if (!File(arkAnalyzerDir).exists()) {
            throw FileNotFoundException("ArkAnalyzer directory does not exist: '$arkAnalyzerDir'. Did you forget to set the '$envVarName' environment variable?")
        }

        val scriptSubPath = "src/save/serializeArkIR"
        val scriptPath = "$arkAnalyzerDir/out/$scriptSubPath.js"
        if (!File(scriptPath).exists()) {
            throw FileNotFoundException("Script file not found: '$scriptPath'. Did you forget to execute 'npm run build' in the arkanalyzer project?")
        }

        val resources = projectDir.resolve("src/test/resources")
        val inputDir = "source"
        val outDir = "etsir/generated/" // Note the final slash!
        println("Generating test resources in '${resources.resolve(outDir)}'...")

        for (file in resources.resolve(inputDir).walkTopDown()) {
            if (file.isFile) {
                val inputPath = file.relativeTo(resources).path
                val cmd = listOf("node", scriptPath, inputPath, outDir)
                println("Running: '${cmd.joinToString(" ")}'")
                val process = ProcessBuilder(cmd).directory(resources).start();
                val ok = process.waitFor(10, TimeUnit.MINUTES)

                val stdout = process.inputStream.bufferedReader().readText().trim()
                if (stdout.isNotBlank()) {
                    println("[STDOUT]\n--------\n$stdout\n--------")
                }
                val stderr = process.errorStream.bufferedReader().readText().trim()
                if (stderr.isNotBlank()) {
                    println("[STDERR]\n--------\n$stderr\n--------")
                }

                if (!ok) {
                    println("Timeout!")
                    process.destroy()
                }
            }
        }

        println("Done generating test resources!")
    }
}
