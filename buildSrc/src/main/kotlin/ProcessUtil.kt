import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Reader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

object ProcessUtil {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val isTimeout: Boolean, // true if the process was terminated due to timeout
    )

    fun run(
        command: List<String>,
        input: Reader = "".reader(),
        timeout: Duration? = null,
        builder: ProcessBuilder.() -> Unit = {},
    ): Result {
        val process = ProcessBuilder(command).apply(builder).start()
        return communicate(process, input, timeout)
    }

    private fun communicate(
        process: Process,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val scope = CoroutineScope(Dispatchers.IO)

        // Handle process input
        val stdinJob = scope.launch {
            process.outputStream.bufferedWriter().use { writer ->
                input.copyTo(writer)
            }
        }

        // Launch output capture coroutines
        val stdoutJob = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdout.appendLine(it) }
            }
        }
        val stderrJob = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderr.appendLine(it) }
            }
        }

        // Wait for completion
        val isTimeout = if (timeout != null) {
            !process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        } else {
            process.waitFor()
            false
        }

        // Wait for all coroutines to finish
        runBlocking {
            joinAll(stdinJob, stdoutJob, stderrJob)
        }

        return Result(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            isTimeout = isTimeout,
        )
    }
}
