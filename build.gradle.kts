import org.jetbrains.dokka.gradle.DokkaTaskPartial

// Use `-Pgroup=com.github.UnitTestBot` to emulate JitPack publishing.
val groupProp = providers.gradleProperty("group").orNull
// Use `-Pversion=1.5` to specify version for publishing.
val versionProp = providers.gradleProperty("version").orNull

group = groupProp ?: "org.jacodb"
version = versionProp ?: "1.4-SNAPSHOT"

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.allopen") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id(Plugins.Dokka)
    id(Plugins.Licenser)
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
        plugin("java")
        plugin("java-library")
        plugin("java-test-fixtures")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin(Plugins.Dokka.id)
        plugin(Plugins.Licenser.id)
        plugin("maven-publish")
        plugin("signing")
        plugin("jacoco")
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
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

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xsam-conversions=class")
            freeCompilerArgs.add("-Xcontext-receivers")
            freeCompilerArgs.add("-Xjvm-default=all")
            allWarningsAsErrors = false
        }
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "1.8"
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all")
        }

        compileJava {
            targetCompatibility = "1.8"
        }
        compileKotlin {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        compileTestJava {
            targetCompatibility = runtimeJavaVersion()
        }
        compileTestFixturesJava {
            targetCompatibility = "1.8"
        }
        compileTestKotlin {
            kotlinOptions {
                jvmTarget = runtimeJavaVersion()
            }
        }
        compileTestFixturesKotlin {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        test {
            useJUnitPlatform {
                excludeTags(Tests.lifecycleTag)
            }
            setup(jacocoTestReport)
        }

        jar {
            manifest {
                attributes["Implementation-Title"] = project.name
                attributes["Implementation-Version"] = archiveVersion
            }
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
            project(":jacodb-benchmarks")
        )
    )
}

val includeDokka: String? by project

configure(
    listOf(
        project(":jacodb-api-common"),
        project(":jacodb-api-jvm"),
        project(":jacodb-api-storage"),
        project(":jacodb-core"),
        project(":jacodb-storage"),
        project(":jacodb-approximations"),
        project(":jacodb-taint-configuration"),
        project(":jacodb-ets"),
    )
) {
    val sourcesJar by tasks.registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.getByName("main").kotlin.srcDirs)
    }

    val dokkaJavadocJar by tasks.registering(Jar::class) {
        dependsOn(tasks.dokkaJavadoc)
        from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }

    artifacts {
        archives(sourcesJar)
        if (includeDokka != null) {
            archives(dokkaJavadocJar)
        }
    }

    publishing {
        publications {
            register<MavenPublication>("main") {
                from(components["java"])
                setOf("apiElements", "runtimeElements")
                    .flatMap { configName -> configurations[configName].hierarchy }
                    .forEach { configuration ->
                        configuration.dependencies.removeIf { dependency ->
                            dependency.version.isNullOrBlank()
                        }
                    }
                addPom()
                signPublication(this@configure)
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/UnitTestBot/jacodb")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }

            // Use `./gradlew publishAllPublicationsToBuildRepository -Pversion=1.5`
            // to publish to `/build/repository` directory in the root project.
            maven {
                name = "Build"
                url = uri(rootProject.layout.buildDirectory.dir("repository"))
            }
        }
    }
}

fun MavenPublication.signPublication(project: Project) {
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
        name.set("JacoDB")
        description.set("Analyse JVM bytecode with pleasure")
        url = "https://www.jacodb.org"
        issueManagement {
            url.set("https://github.com/UnitTestBot/jacodb/issues")
        }
        scm {
            connection.set("scm:git:https://github.com/UnitTestBot/jacodb.git")
            developerConnection.set("scm:git:https://github.com/UnitTestBot/jacodb.git")
            url.set("https://www.jacodb.org")
        }
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
    gradleVersion = "8.9"
    distributionType = Wrapper.DistributionType.ALL
}
