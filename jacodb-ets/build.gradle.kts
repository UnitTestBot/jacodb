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

    testImplementation(kotlin("test"))
    testImplementation(project(":jacodb-analysis"))
    testImplementation(testFixtures(project(":jacodb-core")))
    testImplementation(Libs.mockk)
}

// Example usage:
// ```
// export ARKANALYZER_DIR=~/dev/arkanalyzer
// ./gradlew generateTestResources
// ```
tasks.register("generateTestResources") {
    group = "build"
    description = "Generates test resources from TypeScript files using ArkAnalyzer."
    doLast {
        println("Generating test resources using ArkAnalyzer...")
        val startTime = System.currentTimeMillis()

        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = "arkanalyzer"

        val arkAnalyzerDir = rootDir.resolve(System.getenv(envVarName) ?: run {
            println("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
            defaultArkAnalyzerDir
        })
        if (!arkAnalyzerDir.exists()) {
            throw FileNotFoundException(
                "ArkAnalyzer directory does not exist: '${arkAnalyzerDir.absolutePath}'. " +
                    "Did you forget to set the '$envVarName' environment variable? " +
                    "Current value is '${System.getenv(envVarName)}', " +
                    "current dir is '${File("").absolutePath}'."
            )
        }
        println("Using ArkAnalyzer directory: '${arkAnalyzerDir.relativeTo(rootDir)}'")

        val scriptSubPath = "src/save/serializeArkIR"
        val script = arkAnalyzerDir.resolve("out").resolve("$scriptSubPath.js")
        if (!script.exists()) {
            throw FileNotFoundException(
                "Script file not found: '$script'. " +
                    "Did you forget to execute 'npm run build' in the arkanalyzer project?"
            )
        }
        println("Using script: '${script.relativeTo(arkAnalyzerDir)}'")

        val resources = projectDir.resolve("src/test/resources")
        val inputDir = resources.resolve("samples/source")
        val outputDir = resources.resolve("samples/etsir/ast")
        println("Generating test resources in '${outputDir.relativeTo(projectDir)}'...")

        val cmd: List<String> = listOf(
            "node",
            script.absolutePath,
            "--multi",
            inputDir.relativeTo(resources).path,
            outputDir.relativeTo(resources).path,
        )
        println("Running: '${cmd.joinToString(" ")}'")
        val process = ProcessBuilder(cmd).directory(resources).start()
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

        println("Done generating test resources in %.1fs".format((System.currentTimeMillis() - startTime) / 1000.0))
    }
}
