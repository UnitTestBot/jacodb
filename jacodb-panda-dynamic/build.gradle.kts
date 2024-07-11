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

// Example usage:
// ```
// ARKANALYZER_DIR=~/dev/arkanalyzer ./gradlew generateTestResources
// ```
tasks.register("generateTestResources") {
    doLast {
        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = rootDir.resolve("../arkanalyzer").path

        val arkAnalyzerDir = File(System.getenv(envVarName) ?: run {
            println("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
            defaultArkAnalyzerDir
        })
        if (!arkAnalyzerDir.exists()) {
            throw FileNotFoundException("ArkAnalyzer directory does not exist: '$arkAnalyzerDir'. Did you forget to set the '$envVarName' environment variable?")
        }

        val scriptSubPath = "src/save/serializeArkIR"
        val script = arkAnalyzerDir.resolve("out").resolve("$scriptSubPath.js")
        if (!script.exists()) {
            throw FileNotFoundException("Script file not found: '$script'. Did you forget to execute 'npm run build' in the arkanalyzer project?")
        }

        val resources = projectDir.resolve("src/test/resources")
        val input = resources.resolve("source")
        val output = resources.resolve("etsir/generated")
        println("Generating test resources in '${output.relativeTo(projectDir)}'...")

        val cmd: List<String> = listOf(
            "node",
            script.absolutePath,
            "--multi",
            input.relativeTo(resources).path,
            output.relativeTo(resources).path,
        )
        println("Running: '${cmd.joinToString(" ")}'")
        val process = ProcessBuilder(cmd).directory(resources).start();
        val ok = process.waitFor(10, TimeUnit.MINUTES)

        val stdout = process.inputStream.bufferedReader().readText().trim()
        if (stdout.isNotBlank()) {
            println("[STDOUT]:\n--------\n$stdout\n--------")
        }
        val stderr = process.errorStream.bufferedReader().readText().trim()
        if (stderr.isNotBlank()) {
            println("[STDERR]:\n--------\n$stderr\n--------")
        }

        if (!ok) {
            println("Timeout!")
            process.destroy()
        }

        println("Done generating test resources!")
    }
}
