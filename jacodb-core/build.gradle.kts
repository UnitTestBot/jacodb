
import de.undercouch.gradle.tasks.download.Download
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.nio.file.Paths

val asmVersion: String by rootProject
val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val kmetadataVersion: String by rootProject
val jooqVersion: String by rootProject

buildscript {
    val jooqVersion: String by rootProject
    dependencies {
        classpath(group = "org.jooq", name = "jooq-meta", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-meta-extensions", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-codegen", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-kotlin", version = jooqVersion)
        classpath(group = "org.postgresql", name = "postgresql", version = "42.5.1")
        classpath(group = "org.xerial", name = "sqlite-jdbc", version = "3.39.2.1")
        classpath(group = "com.zaxxer", name = "HikariCP", version = "5.0.1")
    }
}

plugins {
    id("de.undercouch.download") version "5.3.0"
}

kotlin.sourceSets["main"].kotlin {
    srcDir("src/main/jooq")
}

dependencies {
    implementation(project(":jacodb-api"))

    api(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
    api(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-cbor", version = "1.3.3")
    api(group = "info.leadinglight", name = "jdot", version = "1.0")

    implementation(group = "org.postgresql", name = "postgresql", version = "42.5.1")
    implementation(group = "com.zaxxer", name = "HikariCP", version = "5.0.1")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.39.2.1")

    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-metadata-jvm", version = kmetadataVersion)


    testImplementation(project(":jacodb-testing"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter")
    testImplementation(group = "org.unittestbot.soot", name = "soot-utbot-fork", version = "4.4.0-FORK-2")
    testImplementation(group = "com.fasterxml.jackson.core", name = "jackson-core", version = "2.14.1")

    testImplementation(group = "org.jetbrains.kotlinx", name = "kotlinx-benchmark-runtime", version = "0.4.4")
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.9")
}

tasks.register<Download>("downloadIdeaCommunity") {
    src(rootProject.properties.get("intellij_community_url") as String)
    dest("idea-community/idea-community.zip")
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

tasks.register("generateSqlScheme") {
    val databaseLocation = project.properties["database_location"]
    if (databaseLocation != null) {
        val url = "jdbc:sqlite:file:$databaseLocation"
        val driver = "org.sqlite.JDBC"
        GenerationTool.generate(
            Configuration()
                .withJdbc(
                    Jdbc()
                        .withDriver(driver)
                        .withUrl(url)
                )
                .withGenerator(
                    Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(Database())
                        .withGenerate(
                            Generate()
                                .withDeprecationOnUnknownTypes(false)
                        )
                        .withTarget(
                            Target()
                                .withPackageName("org.utbot.jacodb.impl.storage.jooq")
                                .withDirectory(project.file("src/main/jooq").absolutePath)
                        )
                )
        )
    }
}

tasks.register<JavaExec>("generateDocSvgs") {
    dependsOn("testClasses")
    mainClass.set("org.utbot.jcdb.impl.cfg.IRSvgGeneratorKt")
    classpath = sourceSets.test.get().runtimeClasspath
    val svgDocs = Paths.get(rootDir.absolutePath, "docs", "svg").toFile()
    args = listOf(svgDocs.absolutePath)
}
