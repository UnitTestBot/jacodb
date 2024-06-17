import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import java.util.*

object Tests {
    val lifecycleTag = "lifecycle"

}

fun Test.setup(jacocoTestReport: TaskProvider<*>) {
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(jacocoTestReport) // report is always generated after tests run
    val majorJavaVersion =
        Integer.parseInt(StringTokenizer(System.getProperty("java.specification.version"), ".").nextToken())
    jvmArgs = if (majorJavaVersion < 16) {
        listOf(
            "-Xmx24g",
            "-Xms4g",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=heapdump.hprof",
        )
    } else {
        listOf(
            "-Xmx24g",
            "-Xms4g",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=heapdump.hprof",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED", // this is necessary for LMDB
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED" // this is necessary for LMDB
        )
    }
}
