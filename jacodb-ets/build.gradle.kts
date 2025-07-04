import com.github.gradle.node.npm.task.NpmTask
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.minutes

plugins {
    kotlin("plugin.serialization")
    id(Plugins.GradleNode)
}

dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-ets:wire-client"))
    api(project(":jacodb-ets:wire-server"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.jdot)

    testImplementation(kotlin("test"))
    testImplementation(Libs.mockk)
    testImplementation(Libs.slf4j_simple)

    testFixturesImplementation(Libs.kotlin_logging)
    testFixturesImplementation(Libs.junit_jupiter_api)
}

node {
    download = true
    nodeProjectDir.set(file("arkanalyzer"))
}

tasks.register<NpmTask>("runArkAnalyzerServer") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "server")
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
        logger.lifecycle("Generating test resources using ArkAnalyzer...")
        val startTime = System.currentTimeMillis()

        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = "arkanalyzer"

        val arkAnalyzerDir = rootDir.resolve(System.getenv(envVarName) ?: run {
            logger.lifecycle("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
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
        logger.lifecycle("Using ArkAnalyzer directory: '${arkAnalyzerDir.relativeTo(rootDir)}'")

        val scriptSubPath = "src/save/serializeArkIR"
        val script = arkAnalyzerDir.resolve("out").resolve("$scriptSubPath.js")
        if (!script.exists()) {
            throw FileNotFoundException(
                "Script file not found: '$script'. " +
                    "Did you forget to execute 'npm run build' in the arkanalyzer project?"
            )
        }
        logger.lifecycle("Using script: '${script.relativeTo(arkAnalyzerDir)}'")

        val resources = projectDir.resolve("src/test/resources")
        val inputDir = resources.resolve("samples/source")
        val outputDir = resources.resolve("samples/etsir/ast")
        logger.lifecycle("Generating test resources in '${outputDir.relativeTo(projectDir)}'...")

        val cmd: List<String> = listOf(
            "node",
            script.absolutePath,
            "--multi",
            inputDir.relativeTo(resources).path,
            outputDir.relativeTo(resources).path,
            "-t",
        )
        logger.lifecycle("Running: ${cmd.joinToString(" ")}")
        val result = ProcessUtil.run(cmd, timeout = 1.minutes) {
            directory(resources)
        }
        if (result.stdout.isNotBlank()) {
            logger.lifecycle("[STDOUT]:\n--------\n${result.stdout}--------")
        }
        if (result.stderr.isNotBlank()) {
            logger.lifecycle("[STDERR]:\n--------\n${result.stderr}--------")
        }
        if (result.isTimeout) {
            logger.warn("Timeout!")
        }
        if (result.exitCode != 0) {
            logger.warn("Exit code: ${result.exitCode}")
        }

        logger.lifecycle(
            "Done generating test resources in %.1fs"
                .format((System.currentTimeMillis() - startTime) / 1000.0)
        )
    }
}
