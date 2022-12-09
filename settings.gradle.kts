rootProject.name = "jcdb"

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

include("jcdb-api")
include("jcdb-core")
include("jcdb-testing")
include("jcdb-http")
