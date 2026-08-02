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
val tsFrontendDist: File = tsFrontendDir.resolve("dist")
val npmExecutable: String = if (Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "npm"

val npmAvailable: Boolean by lazy {
    try {
        val process = ProcessBuilder(npmExecutable, "--version")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.outputStream.close()
        // Never block the whole build on a hung npm.
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * The ts-frontend is built with npm, which is not guaranteed to be present
 * (offline builds, publishing from a machine without Node). Skipping keeps such
 * builds working; a prebuilt `dist` is still packaged if it exists.
 */
fun Task.onlyIfNpmAvailable() = onlyIf {
    npmAvailable.also { available ->
        if (!available) logger.warn("npm is not available; skipping task '$name'")
    }
}

val installTsFrontend = tasks.register<Exec>("installTsFrontend") {
    group = "build"
    description = "Installs npm dependencies of the ts-frontend."
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "ci")
    inputs.files(tsFrontendDir.resolve("package.json"), tsFrontendDir.resolve("package-lock.json"))
    // `npm ci` wipes node_modules anyway, so snapshotting its tens of thousands of
    // files on every up-to-date check buys nothing; the marker file is enough.
    outputs.file(tsFrontendDir.resolve("node_modules/.package-lock.json"))
    onlyIfNpmAvailable()
}

val buildTsFrontend = tasks.register<Exec>("buildTsFrontend") {
    group = "build"
    description = "Type-checks and builds the self-contained ts-frontend runtime."
    dependsOn(installTsFrontend)
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "run", "build")
    onlyIfNpmAvailable()
    inputs.dir(tsFrontendDir.resolve("src"))
    inputs.files(
        tsFrontendDir.resolve("package.json"),
        tsFrontendDir.resolve("package-lock.json"),
        tsFrontendDir.resolve("tsconfig.json"),
    )
    inputs.dir(tsFrontendDir.resolve("scripts"))
    outputs.dir(tsFrontendDist)
}

val packageTsFrontendRuntime = tasks.register<Zip>("packageTsFrontendRuntime") {
    group = "build"
    description = "Packages the ts-frontend script and TypeScript standard libraries."
    dependsOn(buildTsFrontend)
    from(tsFrontendDist) {
        include("index.js", "lib*.d.ts")
    }
    archiveFileName.set("runtime.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/etsFrontend"))
    // Without npm and without a prebuilt `dist` there is nothing to package; skipping keeps
    // offline builds and publishing from a machine without Node working (see onlyIfNpmAvailable).
    onlyIf {
        (tsFrontendDist.resolve("index.js").isFile || npmAvailable).also { runnable ->
            if (!runnable) logger.warn("ts-frontend was not built and npm is unavailable; skipping task '$name'")
        }
    }
    // A silently incomplete archive would be published and only fail at runtime:
    // the consumer (LoadEtsFile.kt) checks for index.js but not for the type libraries.
    doFirst {
        require(tsFrontendDist.resolve("index.js").isFile) {
            "ts-frontend was not built: '${tsFrontendDist.resolve("index.js")}' is missing"
        }
        val libs = tsFrontendDist.listFiles()
            ?.count { it.isFile && it.name.startsWith("lib") && it.name.endsWith(".d.ts") }
            ?: 0
        require(libs > 0) {
            "ts-frontend dist contains no 'lib*.d.ts' type libraries in '$tsFrontendDist'; " +
                "the packaged runtime would be silently broken"
        }
        logger.info("Packaging ts-frontend runtime: index.js + $libs type libraries")
    }
}

tasks.processResources {
    dependsOn(packageTsFrontendRuntime)
    from(packageTsFrontendRuntime.flatMap { it.archiveFile }) {
        into("ets-frontend")
    }
}

val testTsFrontend = tasks.register<Exec>("testTsFrontend") {
    group = "verification"
    description = "Runs the ts-frontend unit tests (vitest)."
    dependsOn(installTsFrontend)
    workingDir = tsFrontendDir
    commandLine(npmExecutable, "test")
    inputs.dir(tsFrontendDir.resolve("src"))
    inputs.dir(tsFrontendDir.resolve("test"))
    inputs.files(
        tsFrontendDir.resolve("package.json"),
        tsFrontendDir.resolve("package-lock.json"),
        tsFrontendDir.resolve("tsconfig.json"),
        tsFrontendDir.resolve("vitest.config.ts"),
    )
    outputs.file(layout.buildDirectory.file("test-results/testTsFrontend/success.marker"))
    doLast {
        val marker = layout.buildDirectory.file("test-results/testTsFrontend/success.marker").get().asFile
        marker.parentFile.mkdirs()
        marker.writeText("ok")
    }
    onlyIfNpmAvailable()
}

tasks.test {
    dependsOn(buildTsFrontend)
}

tasks.check {
    dependsOn(testTsFrontend)
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
        // NB: keep the accepted aliases in sync with `EtsIrProvider.default()`
        // (jacodb-ets/src/main/kotlin/org/jacodb/ets/utils/LoadEtsFile.kt); this task
        // runs before the module is compiled and therefore cannot call it.
        val provider = when (System.getenv("ETS_IR_PROVIDER")?.trim()?.lowercase()) {
            "arkanalyzer", "ark-analyzer", "ark_analyzer" -> "arkanalyzer"
            else -> "ts-frontend"
        }
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
            System.getenv("NODE_EXECUTABLE") ?: "node",
            script.absolutePath,
            "--multi",
            inputDir.relativeTo(resources).path,
            outputDir.relativeTo(resources).path,
            "-t",
        )
        println("Running: '${cmd.joinToString(" ")}'")
        val processLog = temporaryDir.resolve("generate-test-resources.log")
        val process = ProcessBuilder(cmd)
            .directory(resources)
            .redirectErrorStream(true)
            .redirectOutput(processLog)
            .start()
        val ok = process.waitFor(10, TimeUnit.MINUTES)

        if (!ok) {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
        // Print the generator output BEFORE failing: otherwise a timeout leaves
        // the only diagnostics buried in build/tmp.
        val processOutput = processLog.readText().trim()
        if (processOutput.isNotBlank()) {
            println("[GENERATOR OUTPUT]:\n--------\n$processOutput\n--------")
        }
        if (!ok) {
            throw GradleException("Test resource generation timed out")
        }
        if (process.exitValue() != 0) {
            throw GradleException("Test resource generation failed with exit code ${process.exitValue()}")
        }

        println("Done generating test resources in %.1fs".format((System.currentTimeMillis() - startTime) / 1000.0))
    }
}
