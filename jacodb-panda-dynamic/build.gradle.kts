import java.util.concurrent.TimeoutException
import kotlin.time.measureTime

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


tasks.register("buildTestResources") {
    doLast {
        val ENV_VAR_NAME = "ARKANALYZER_DIR"
        val arkAnalyzerDir =
            System.getenv(ENV_VAR_NAME)
                ?: error("Please, set $ENV_VAR_NAME variable")
        val scriptPath = "$arkAnalyzerDir/scripts/convertAllTsToJson.ts"
        val resourcesDir = layout.projectDirectory.dir("src/test/resources")
        val sourceDir = resourcesDir.dir("source")
        val irDir = resourcesDir.dir("autoir")
        println("Building test resources for '$sourceDir' into '$irDir'...")
        val cmd = "npx ts-node $scriptPath ${sourceDir.asFile.absolutePath} ${irDir.asFile.absolutePath}"
        println("Running: '$cmd'...")
        val worker = Runtime.getRuntime().exec(cmd)
        if (!worker.waitFor(2, TimeUnit.MINUTES)) {
            worker.destroy()
            throw TimeoutException("Build cancelled")
        }
        println("Stdout: ${worker.inputStream.bufferedReader().readText()}")
        println("Stderr: ${worker.errorStream.bufferedReader().readText()}")
        println("Finished building test resources into '$irDir'")
    }
}
