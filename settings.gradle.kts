rootProject.name = "jacodb"

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.0.25"
}

gitHooks {
    preCommit {
        // Content can be added at the bottom of the script
        from(file("pre-commit").toURI().toURL())
    }
    createHooks() // actual hooks creation
}

include("jacodb-api")
include("jacodb-core")
include("jacodb-http")
include("jacodb-analysis")
include("jacodb-examples")
