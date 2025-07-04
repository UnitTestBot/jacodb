rootProject.name = "jacodb"

plugins {
    id("com.gradle.develocity") version "3.18.2"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.11"
}

develocity {
    buildScan {
        // Accept the term of use for the build scan plugin:
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")

        // Publish build scans on-demand, when `--scan` option is provided:
        publishing.onlyIf { false }
    }
}

gitHooks {
    preCommit {
        from(file("pre-commit"))
    }
    createHooks(true)
}

val localProperties = java.util.Properties().apply {
    if (file("local.properties").exists()) {
        file("local.properties").reader().use(::load)
    }
}

val enableEts = run {
    fun String.isTrue(): Boolean = this in listOf("true", "yes", "1")
    // Note: we check for presence of the property first to maintain the following behavior:
    //  1. If the property is set via command line (`-PenableEts=true`)
    //     or via `gradle.properties` file, it takes precedence.
    //  2. If the property is not set, we check the `local.properties` file.
    //  3. If neither is set, the default is `false`.
    if (providers.gradleProperty("enableEts").isPresent) {
        providers.gradleProperty("enableEts").get().isTrue()
    } else {
        localProperties.getProperty("enableEts", "false").isTrue()
    }
}
if (enableEts) {
    if (JavaVersion.current().isJava11Compatible) {
        include("jacodb-ets")
        include("jacodb-ets:wire-protos")
        include("jacodb-ets:wire-client")
        include("jacodb-ets:wire-server")
    } else {
        throw GradleException("jacodb-ets requires Java 11 or higher, but the current Java version is ${JavaVersion.current()}")
    }
}

include("jacodb-api-common")
include("jacodb-api-jvm")
include("jacodb-api-storage")
include("jacodb-core")
include("jacodb-storage")
include("jacodb-examples")
include("jacodb-benchmarks")
include("jacodb-approximations")
include("jacodb-taint-configuration")
