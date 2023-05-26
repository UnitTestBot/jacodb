val coroutinesVersion: String by rootProject

dependencies {
    api(project(":jacodb-core"))
    api(project(":jacodb-api"))
    api(project(":jacodb-analysis"))

    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-reactor", version = coroutinesVersion)
    implementation(group = "org.unittestbot.soot", name = "soot-utbot-fork", version = "4.4.0-FORK-2")
    implementation(group =  "org.slf4j", name = "slf4j-simple", version = "1.6.1")
}

tasks.create<JavaExec>("runJacoDBPerformanceAnalysis") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.jacodb.examples.analysis.PerformanceMetricsKt")
}

tasks.create<JavaExec>("runSootPerformanceAnalysis") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.jacodb.examples.analysis.SootPerformanceMetricsKt")
}