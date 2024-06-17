/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.nio.file.Paths

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(Libs.jooq_meta)
        classpath(Libs.jooq_meta_extensions)
        classpath(Libs.jooq_codegen)
        classpath(Libs.jooq_kotlin)
        // classpath(Libs.postgresql)
        // classpath(Libs.hikaricp)
        classpath(Libs.sqlite)
    }
}

plugins {
    `java-test-fixtures`
}

kotlin.sourceSets["main"].kotlin {
    srcDir("src/main/jooq")
    srcDir("src/main/ers/jooq")
}

dependencies {
    implementation(project(":jacodb-api-jvm"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_metadata_jvm)
    implementation(Libs.kotlinx_serialization_cbor)
    implementation(Libs.jdot)
    implementation(Libs.guava)
    implementation(Libs.sqlite)
    implementation(Libs.xodusUtils)
    implementation(Libs.xodusEnvironment)
    implementation(Libs.hikaricp)
    implementation(Libs.lmdb_java)
    implementation(Libs.rocks_db)

    testImplementation(Libs.javax_activation)
    testImplementation(Libs.javax_mail)
    testImplementation(Libs.joda_time)
    testImplementation(Libs.slf4j_simple)
    testImplementation(Libs.hikaricp)

    testFixturesImplementation(project(":jacodb-api-jvm"))
    testFixturesImplementation(kotlin("reflect"))
    testFixturesImplementation(platform(Libs.junit_bom))
    testFixturesImplementation(Libs.junit_jupiter)
    testFixturesImplementation(Libs.guava)
    testFixturesImplementation(Libs.jetbrains_annotations)
    testFixturesImplementation(Libs.kotlinx_coroutines_core)
}

tasks {
    register("generateSqlScheme") {
        doLast {
            generateSqlScheme(
                dbLocation = "src/main/resources/sqlite/empty.db",
                sourceSet = "src/main/jooq",
                packageName = "org.jacodb.impl.storage.jooq"
            )
        }
    }

    register("generateErsSqlScheme") {
        doLast {
            generateSqlScheme(
                dbLocation = "src/main/resources/ers/sqlite/empty.db",
                sourceSet = "src/main/ers/jooq",
                packageName = "org.jacodb.impl.storage.ers.jooq"
            )
        }
    }

    register<JavaExec>("generateDocSvgs") {
        dependsOn("testClasses")
        mainClass.set("org.utbot.jcdb.impl.cfg.IRSvgGeneratorKt")
        classpath = sourceSets.test.get().runtimeClasspath
        val svgDocs = Paths.get(rootDir.absolutePath, "docs", "svg").toFile()
        args = listOf(svgDocs.absolutePath)
    }

    processResources {
        filesMatching("**/*.properties") {
            expand("version" to project.version)
        }
    }
}

fun generateSqlScheme(
    dbLocation: String,
    sourceSet: String,
    packageName: String
) {
    val url = "jdbc:sqlite:file:${project.projectDir}/$dbLocation"
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
                            .withPackageName(packageName)
                            .withDirectory(project.file(sourceSet).absolutePath)
                    )
            )
    )
}