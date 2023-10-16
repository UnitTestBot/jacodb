rootProject.name = "jacodb"

plugins {
    `gradle-enterprise`
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.11"
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
include("jacodb-analysis")
include("jacodb-examples")
include("jacodb-benchmarks")
include("jacodb-cli")
include("jacodb-approximations")
