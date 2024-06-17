import de.undercouch.gradle.tasks.download.Download
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    `java-test-fixtures`
    id(Plugins.GradleDownload)
    id(Plugins.KotlinxBenchmark)
}

dependencies {
    implementation(project(":jacodb-api-jvm"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_serialization_cbor)
    implementation(Libs.jdot)
    implementation(Libs.soot_utbot_fork)
    implementation(Libs.sootup_core)
    implementation(Libs.sootup_java_bytecode)

    testImplementation(Libs.kotlinx_benchmark_runtime)
}

benchmark {
    configurations {
        named("main") { // main configuration is created automatically, but you can change its defaults
            warmups = 3 // number of warmup iterations
            iterations = 5 // number of iterations
        }
    }

    // Setup configurations
    targets {
        // This one matches sourceSet name above
        register("test") {
            this as JvmBenchmarkTarget
            jmhVersion = Versions.jmh
        }
    }
}

benchmark {
    configurations {
        named("main") {
            include("JcdbLifeCycleBenchmarks")
            include("RestoreJcdbBenchmark")
        }
        register("jcdb") {
            include("JcdbBenchmarks")
        }
        register("jcdbRAM") {
            include("JcdbRAMBenchmarks")
        }
        register("soot") {
            include("SootBenchmarks")
        }
        register("sootup") {
            include("SootupBenchmarks")
        }
        register("instructions") {
            include("JcInstructionsBenchmark")
        }
        register("awaitBackground") {
            include("JcdbJvmBackgroundBenchmarks")
            include("JcdbAllClasspathBackgroundBenchmarks")
            include("JcdbIdeaBackgroundBenchmarks")
        }
        register("ramAwaitBackground") {
            include("JcdbRAMJvmBackgroundBenchmarks")
            include("JcdbRAMAllClasspathBackgroundBenchmarks")
            include("JcdbRAMIdeaBackgroundBenchmarks")
        }
        register("cache") {
            warmups = 5
            iterations = 10
            include("GuavaCacheBenchmarks")
            include("XodusCacheBenchmarks")
        }
    }
}

tasks.register<Download>("downloadIdeaCommunity") {
    src(rootProject.properties["intellij_community_url"] as String)
    dest("idea-community/idea-community.zip")
    onlyIfModified(true)
}

tasks.register<Copy>("downloadAndUnzipIdeaCommunity") {
    dependsOn("downloadIdeaCommunity")
    val downloadIdeaCommunity by tasks.getting(Download::class)

    from(zipTree(downloadIdeaCommunity.dest))
    into("idea-community/unzip")
}

val benchmarkTasks = listOf(
    "testJcdbBenchmark",
    "testSootBenchmark",
    "testAwaitBackgroundBenchmark",
)
tasks.matching { it.name in benchmarkTasks }.configureEach {
    dependsOn("downloadAndUnzipIdeaCommunity")
}
