import de.undercouch.gradle.tasks.download.Download
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

val asmVersion: String by rootProject
val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val kmetadataVersion: String by rootProject
val jooqVersion: String by rootProject

plugins {
    id("de.undercouch.download") version "5.3.0"
    `java-test-fixtures`
    id("org.jetbrains.kotlinx.benchmark") version "0.4.4"
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
            jmhVersion = "1.21"
        }
    }
}


dependencies {
    implementation(project(":jacodb-api"))
    implementation(project(":jacodb-core"))
    implementation(testFixtures(project(":jacodb-core")))

    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-cbor", version = "1.3.3")
    implementation(group = "info.leadinglight", name = "jdot", version = "1.0")

    implementation(group = "org.unittestbot.soot", name = "soot-utbot-fork", version = "4.4.0-FORK-2")
    implementation(group = "org.soot-oss", name = "sootup.core", version = "1.0.0")
    implementation(group = "org.soot-oss", name = "sootup.java.bytecode", version = "1.0.0")
}

tasks.register<Download>("downloadIdeaCommunity") {
    src(rootProject.properties.get("intellij_community_url") as String)
    dest("idea-community/idea-community.zip")
    onlyIfModified(true)
}

tasks.register<Copy>("downloadAndUnzipIdeaCommunity") {
    dependsOn("downloadIdeaCommunity")
    val downloadIdeaCommunity by tasks.getting(Download::class)

    from(zipTree(downloadIdeaCommunity.dest))
    into("idea-community/unzip")
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
        register("soot") {
            include("SootBenchmarks")
        }
        register("sootup") {
            include("SootupBenchmarks")
        }
        register("awaitBackground") {
            include("JcdbJvmBackgroundBenchmarks")
            include("JcdbAllClasspathBackgroundBenchmarks")
            include("JcdbIdeaBackgroundBenchmarks")
        }
    }
}

val benchmarkTasks = listOf("testJcdbBenchmark", "testSootBenchmark", "testAwaitBackgroundBenchmark")
tasks.matching { benchmarkTasks.contains(it.name) }.configureEach {
    dependsOn("downloadAndUnzipIdeaCommunity")
}