import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.Reader
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.time.Duration

/** Java 8 exposes parent cleanup only; process-tree cleanup is available on Java 9 and later. */
private fun destroyDescendantsIfSupported(process: Process) {
    val handleClass = try {
        Class.forName("java.lang.ProcessHandle")
    } catch (_: ClassNotFoundException) {
        return
    }

    try {
        val handle = Process::class.java.getMethod("toHandle").invoke(process)
        val descendants = handleClass.getMethod("descendants").invoke(handle) as Stream<*>
        val destroy = handleClass.getMethod("destroyForcibly")
        descendants.use { children ->
            children.forEach { child -> destroy.invoke(child) }
        }
    } catch (error: InvocationTargetException) {
        val cause = error.targetException
        if (cause is RuntimeException) throw cause
        throw IllegalStateException("Failed to terminate process descendants", cause)
    } catch (error: ReflectiveOperationException) {
        throw IllegalStateException("Cannot access process-tree cleanup on this runtime", error)
    }
}

object ProcessUtil {
    private const val INPUT_CLEANUP_TIMEOUT_MILLIS = 1_000L

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val isTimeout: Boolean,
    )

    /**
     * Capture output without waiting for inherited pipes to reach EOF after the parent exits.
     * The caller retains ownership of [input]; a blocking reader must respond to interruption.
     * Failure to stop input within the cleanup grace period is reported instead of waiting forever.
     * A reader that ignores interruption remains caller-owned even after that failure.
     */
    fun run(
        command: List<String>,
        input: Reader = "".reader(),
        timeout: Duration? = null,
        builder: ProcessBuilder.() -> Unit = {},
    ): Result {
        val directory = Files.createTempDirectory("jacodb-process-")
        var runFailure: Throwable? = null
        try {
            val processBuilder = ProcessBuilder(command).apply(builder)
            val stdoutFile = directory.resolve("stdout")
            val stderrFile = directory.resolve("stderr")
            val captureStdout = processBuilder.redirectOutput() == ProcessBuilder.Redirect.PIPE
            val captureStderr = !processBuilder.redirectErrorStream() &&
                processBuilder.redirectError() == ProcessBuilder.Redirect.PIPE
            val writeStdin = processBuilder.redirectInput() == ProcessBuilder.Redirect.PIPE
            if (captureStdout) {
                processBuilder.redirectOutput(stdoutFile.toFile())
            }
            if (captureStderr) {
                processBuilder.redirectError(stderrFile.toFile())
            }

            val process = processBuilder.start()
            try {
                val inputPump = if (writeStdin) InputPump(process, input) else null
                inputPump.use {
                    val isTimeout = waitForProcess(process, timeout)
                    if (isTimeout) {
                        terminateAndReap(process)
                    }

                    return Result(
                        exitCode = process.exitValue(),
                        stdout = if (captureStdout) readSnapshot(stdoutFile) else "",
                        stderr = if (captureStderr) readSnapshot(stderrFile) else "",
                        isTimeout = isTimeout,
                    )
                }
            } catch (failure: Throwable) {
                try {
                    terminateAndReap(process)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                if (failure is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw failure
            }
        } catch (failure: Throwable) {
            runFailure = failure
            throw failure
        } finally {
            try {
                Files.deleteIfExists(directory.resolve("stdout"))
                Files.deleteIfExists(directory.resolve("stderr"))
                Files.deleteIfExists(directory)
            } catch (cleanupFailure: Throwable) {
                val failure = runFailure
                if (failure == null) {
                    throw cleanupFailure
                }
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun waitForProcess(process: Process, timeout: Duration?): Boolean {
        try {
            return if (timeout != null) {
                !process.waitFor(timeout.inWholeNanoseconds.coerceAtLeast(0), TimeUnit.NANOSECONDS)
            } else {
                process.waitFor()
                false
            }
        } catch (interrupted: InterruptedException) {
            // Terminate first, so blocked stdin writes can finish before the pump is joined.
            try {
                terminateAndReap(process)
            } catch (cleanupFailure: Throwable) {
                interrupted.addSuppressed(cleanupFailure)
            }
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    /** A descendant may keep appending; capture only the bytes present when the parent finished. */
    private fun readSnapshot(file: Path): String {
        var remaining = Files.size(file)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val captured = ByteArrayOutputStream()
        Files.newInputStream(file).use { stream ->
            while (remaining > 0) {
                val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) {
                    break
                }
                captured.write(buffer, 0, count)
                remaining -= count
            }
        }
        return captured.toByteArray().toString(Charsets.UTF_8)
    }

    private class InputPump(private val process: Process, input: Reader) : AutoCloseable {
        @Volatile
        private var stopping = false

        @Volatile
        private var failure: Throwable? = null

        private val worker = Thread({
            try {
                process.outputStream.bufferedWriter().use { writer ->
                    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                    while (!stopping) {
                        val count = try {
                            input.read(buffer)
                        } catch (error: Throwable) {
                            if (!stopping) {
                                failure = error
                            }
                            throw error
                        }
                        if (count < 0) {
                            break
                        }
                        writer.write(buffer, 0, count)
                    }
                }
            } catch (error: Throwable) {
                // A process may finish without consuming all stdin. Preserve other input failures.
                val expectedClosedPipe = error is IOException && !process.isAlive
                if (!stopping && !expectedClosedPipe) {
                    failure = error
                }
            }
        }, "jacodb-process-input").apply {
            isDaemon = true
            start()
        }

        override fun close() {
            stopping = true
            worker.interrupt()
            var interrupted = Thread.interrupted()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INPUT_CLEANUP_TIMEOUT_MILLIS)
            try {
                while (worker.isAlive) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) {
                        error("Process input did not stop after interruption within the cleanup deadline")
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedJoin(worker, remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                failure?.let { throw it }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    internal fun terminateAndReap(process: Process) {
        try {
            destroyDescendantsIfSupported(process)
        } finally {
            process.destroyForcibly()
            var interrupted = false
            try {
                while (true) {
                    try {
                        process.waitFor()
                        break
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
