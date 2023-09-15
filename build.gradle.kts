import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val semVer: String? by project
val includeDokka: String? by project

group = "org.jacodb"
version = semVer ?: "1.2-SNAPSHOT"

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.allopen") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin apply false
    with(Plugins.Dokka) { id(id) version (version) }
    with(Plugins.Licenser) { id(id) version (version) }
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    signing
    jacoco
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin(Plugins.Dokka.id)
        plugin(Plugins.Licenser.id)
        plugin("maven-publish")
        plugin("signing")
        plugin("jacoco")
    }

    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/orgunittestbotsoot-1004/")
        maven("https://plugins.gradle.org/m2")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/maven-central")
    }

    dependencies {
        // Kotlin
        implementation(platform(kotlin("bom")))
        implementation(kotlin("stdlib-jdk8"))

        // JUnit
        testImplementation(platform(Libs.junit_bom))
        testImplementation(Libs.junit_jupiter)

        // Test dependencies
        testRuntimeOnly(Libs.guava)
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

        test {
            useJUnitPlatform {
                excludeTags(Tests.lifecycleTag)
            }
            setup(jacocoTestReport)
        }

        val lifecycleTest by creating(Test::class) {
            useJUnitPlatform {
                includeTags(Tests.lifecycleTag)
            }
            setup(jacocoTestReport)
        }

        jacocoTestReport {
            classDirectories.setFrom(files(classDirectories.files.map {
                fileTree(it) {
                    excludes.add("org/jacodb/impl/storage/jooq/**")
                }
            }))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        withType<DokkaTaskPartial> {
            dokkaSourceSets.configureEach {
                includes.from("README.md")
            }
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
}

tasks.dokkaHtmlMultiModule {
    removeChildTasks(
        listOf(
            project(":jacodb-examples"),
            project(":jacodb-cli"),
            project(":jacodb-benchmarks")
        )
    )
}

val repoUrl: String? = project.properties["repoUrl"] as? String
    ?: "https://maven.pkg.github.com/UnitTestBot/jacodb"

if (!repoUrl.isNullOrEmpty()) {
    configure(
        listOf(
            project(":jacodb-api"),
            project(":jacodb-core"),
            project(":jacodb-analysis"),
            project(":jacodb-approximations"),
        )
    ) {
        tasks {
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
                if (includeDokka != null) {
                    archives(dokkaJavadocJar)
                }
            }

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
                    signPublication(this@configure)
                }
            }

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
        val gpgKeyValue = gpgKey?.removeSurrounding("\"")
        val gpgPasswordValue = gpgPassphrase

        if (gpgKeyValue != null && gpgPasswordValue != null) {
            useInMemoryPgpKeys(gpgKeyValue, gpgPasswordValue)

            sign(this@signPublication)
        }
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
                email.set("volkov.ivan2004@gmail.com")
            }
            developer {
                id.set("AbdullinAM")
                name.set("Azat Abdullin")
                email.set("azat.aam@gmail.com")
            }
            developer {
                id.set("CaelmBleidd")
                name.set("Alexey Menshutin")
                email.set("alex.menshutin99@gmail.com")
            }
            developer {
                id.set("sergeypospelov")
                name.set("Sergey Pospelov")
                email.set("sergeypospelov59@gmail.com")
            }
            developer {
                id.set("UnitTestBot")
                name.set("UnitTestBot Team")
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.3"
    distributionType = Wrapper.DistributionType.ALL
}
