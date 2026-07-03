import org.apache.tools.ant.taskdefs.condition.Os
import java.io.FileNotFoundException

plugins {
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    api(project(":jacodb-api-common"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.jdot)

    testImplementation(kotlin("test"))
    testImplementation(Libs.mockk)

    testFixturesImplementation(Libs.kotlin_logging)
    testFixturesImplementation(Libs.junit_jupiter_api)
}

// ----------------------------------------------------------------------------
// Native TypeScript frontend (ts-frontend)
// ----------------------------------------------------------------------------

val tsFrontendDir: File = projectDir.resolve("ts-frontend")
val npmExecutable: String = if (Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "npm"

fun isNpmAvailable(): Boolean = try {
    ProcessBuilder(npmExecutable, "--version").start().waitFor() == 0
} catch (_: Exception) {
    false
}

val installTsFrontend = tasks.register<Exec>("installTsFrontend") {
    group = "build"
    description = "Installs npm dependencies of the ts-frontend."
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "ci")
    inputs.files(tsFrontendDir.resolve("package.json"), tsFrontendDir.resolve("package-lock.json"))
    outputs.dir(tsFrontendDir.resolve("node_modules"))
    onlyIf {
        isNpmAvailable().also { available ->
            if (!available) logger.warn("npm is not available; skipping ts-frontend install")
        }
    }
}

val buildTsFrontend = tasks.register<Exec>("buildTsFrontend") {
    group = "build"
    description = "Compiles the ts-frontend (TypeScript -> dist)."
    dependsOn(installTsFrontend)
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "run", "build")
    inputs.dir(tsFrontendDir.resolve("src"))
    inputs.files(tsFrontendDir.resolve("package.json"), tsFrontendDir.resolve("tsconfig.json"))
    outputs.dir(tsFrontendDir.resolve("dist"))
    onlyIf {
        isNpmAvailable().also { available ->
            if (!available) logger.warn("npm is not available; skipping ts-frontend build")
        }
    }
}

tasks.register<Exec>("testTsFrontend") {
    group = "verification"
    description = "Runs the ts-frontend unit tests (vitest)."
    dependsOn(installTsFrontend)
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "test")
    onlyIf {
        isNpmAvailable().also { available ->
            if (!available) logger.warn("npm is not available; skipping ts-frontend tests")
        }
    }
}

tasks.test {
    dependsOn(buildTsFrontend)
    systemProperty("ets.frontend.dir", tsFrontendDir.absolutePath)
}

// ----------------------------------------------------------------------------
// Test resource generation
// ----------------------------------------------------------------------------

// Example usage:
// ```
// ./gradlew generateTestResources
// # or with the legacy ArkAnalyzer provider:
// export ARKANALYZER_DIR=~/dev/arkanalyzer
// ETS_IR_PROVIDER=arkanalyzer ./gradlew generateTestResources
// ```
tasks.register("generateTestResources") {
    group = "build"
    description = "Generates test resources (EtsIR JSON) from TypeScript sample files."
    dependsOn(buildTsFrontend)
    doLast {
        val provider = (System.getenv("ETS_IR_PROVIDER") ?: "ts-frontend").lowercase()
        println("Generating test resources using provider: $provider")
        val startTime = System.currentTimeMillis()

        val script: File = when (provider) {
            "arkanalyzer" -> {
                val envVarName = "ARKANALYZER_DIR"
                val arkAnalyzerDir = rootDir.resolve(System.getenv(envVarName) ?: "arkanalyzer")
                if (!arkAnalyzerDir.exists()) {
                    throw FileNotFoundException(
                        "ArkAnalyzer directory does not exist: '${arkAnalyzerDir.absolutePath}'. " +
                            "Did you forget to set the '$envVarName' environment variable?"
                    )
                }
                arkAnalyzerDir.resolve("out/src/save/serializeArkIR.js").also {
                    if (!it.exists()) {
                        throw FileNotFoundException(
                            "Script file not found: '$it'. " +
                                "Did you forget to execute 'npm run build' in the arkanalyzer project?"
                        )
                    }
                }
            }

            else -> tsFrontendDir.resolve("dist/index.js").also {
                if (!it.exists()) {
                    throw FileNotFoundException(
                        "Script file not found: '$it'. " +
                            "Did you forget to execute 'npm run build' in ts-frontend?"
                    )
                }
            }
        }
        println("Using script: '$script'")

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
            "-t",
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
            process.destroy()
            throw GradleException("Test resource generation timed out")
        }
        if (process.exitValue() != 0) {
            throw GradleException("Test resource generation failed with exit code ${process.exitValue()}")
        }

        println("Done generating test resources in %.1fs".format((System.currentTimeMillis() - startTime) / 1000.0))
    }
}
