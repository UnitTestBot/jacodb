rootProject.name = "jacodb"

plugins {
    `gradle-enterprise`
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.11"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

gitHooks {
    preCommit {
        from(file("pre-commit"))
    }
    createHooks(true)
}

include("jacodb-api")
include("jacodb-core")
include("jacodb-examples")
include("jacodb-benchmarks")
include("jacodb-cli")
include("jacodb-approximations")
include("jacodb-taint-configuration")
include("jacodb-analysis:common")
include("jacodb-analysis:actors")
include("jacodb-analysis:ifds")
include("jacodb-analysis:taint")
