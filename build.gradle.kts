import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val junit5Version: String by project
val semVer: String? by project

group = "org.jacodb"

project.version = semVer ?: "1.0-SNAPSHOT"

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
    }
}

plugins {
    val kotlinVersion = "1.7.21"

    `java-library`
    `maven-publish`
    signing
    `java-test-fixtures`
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    id("org.jetbrains.dokka") version "1.7.20"

    id("org.cadixdev.licenser") version "0.6.1"
    jacoco
}

repositories {
    mavenCentral()
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("maven-publish")
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin("org.cadixdev.licenser")
        plugin("jacoco")
        plugin("signing")
        plugin("org.jetbrains.dokka")
    }

    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/orgunittestbotsoot-1004/")
        maven("https://plugins.gradle.org/m2")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/maven-central")
    }

    dependencies {
        implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)

        implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
        implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)

        testImplementation("org.junit.jupiter:junit-jupiter") {
            version {
                strictly(junit5Version)
            }
        }
        testImplementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
            options.encoding = "UTF-8"
            options.compilerArgs = options.compilerArgs + "-Xlint:all"
        }
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xallow-result-return-type",
                    "-Xsam-conversions=class",
                    "-Xcontext-receivers",
                    "-Xjvm-default=all"
                )
                allWarningsAsErrors = false
            }
        }
        compileTestKotlin {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xallow-result-return-type",
                    "-Xsam-conversions=class",
                    "-Xcontext-receivers"
                )
                allWarningsAsErrors = false
            }
        }

        jacocoTestReport {
            dependsOn(test) // tests are required to run before generating the report
            reports {
                csv.required.set(true)
            }

        }

        withType<Test> {
            useJUnitPlatform()
            jvmArgs = listOf("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=heapdump.hprof")
            testLogging {
                events("passed", "skipped", "failed")
            }
            finalizedBy(jacocoTestReport) // report is always generated after tests run
        }

        val dokkaJavadocJar by creating(Jar::class) {
            dependsOn(dokkaJavadoc)
            from(dokkaJavadoc.flatMap { it.outputDirectory })
            archiveClassifier.set("javadoc")
        }

        val sourcesJar by creating(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.getByName("main").kotlin.srcDirs)
        }

        artifacts {
            archives(sourcesJar)
            archives(dokkaJavadocJar)
        }
    }

    allOpen {
        annotation("org.openjdk.jmh.annotations.State")
    }

    license {
        include("**/*.kt")
        include("**/*.java")
        header(rootProject.file("docs/copyright/COPYRIGHT_HEADER.txt"))
    }

    publishing {
        publications {
            register<MavenPublication>("jar") {
                from(components["java"])
                artifact(tasks.named("sourcesJar"))
                artifact(tasks.named("dokkaJavadocJar"))

                groupId = "org.jacodb"
                artifactId = project.name
                addPom()
                signPublication(this@allprojects)
            }
        }
    }

}

val repoUrl: String? = project.properties["repoUrl"] as? String ?: "https://maven.pkg.github.com/UnitTestBot/jacodb"

if (!repoUrl.isNullOrEmpty()) {
    configure(
        listOf(
            project(":jacodb-api"),
            project(":jacodb-core"),
//            project(":jacodb-analysis"),
        )
    ) {
        publishing {
            repositories {
                maven {
                    name = "repo"
                    url = uri(repoUrl)
                    val actor: String? by project
                    val token: String? by project

                    credentials {
                        username = actor
                        password = token
                    }
                }
            }
        }
    }
}


fun MavenPublication.signPublication(project: Project) = with(project) {
    signing {
        val gpgKey: String? by project
        val gpgPassphrase: String? by project
        useInMemoryPgpKeys(gpgKey, gpgPassphrase)

        sign(this@signPublication)
    }
}

fun MavenPublication.addPom() {
    pom {
        packaging = "jar"
        name.set("org.jacodb")
        description.set("analyse JVM bytecode with pleasure")
        issueManagement {
            url.set("https://github.com/UnitTestBot/jacodb/issues")
        }
        scm {
            connection.set("scm:git:https://github.com/UnitTestBot/jacodb.git")
            developerConnection.set("scm:git:https://github.com/UnitTestBot/jacodb.git")
            url.set("https://www.jacodb.org")
        }
        url.set("https://www.jacodb.org")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("lehvolk")
                name.set("Alexey Volkov")
                email.set("lehvolk@yandex.ru")
            }
            developer {
                id.set("volivan239")
                name.set("Ivan Volkov")
                email.set("lehvolk@yandex.ru")
            }
            developer {
                id.set("AbdullinAM")
                name.set("Azat Abdullin")
                email.set("azat.aam@gmail.com")
            }
            developer {
                id.set("UnitTestBot")
                name.set("UnitTestBot Team")
            }
        }
    }
}