package com.vortix.app.data.battery.shizuku

import android.os.RemoteException
import com.vortix.app.data.battery.BatteryStatsWakelockParser
import com.vortix.app.domain.analytics.ShizukuPackageCommandFactory
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess

/**
 * Runs in Shizuku's authorized shell/root user-service process. It returns filtered
 * Batterystats wakelock rows and executes validated package actions using argv arrays.
 */
@Keep
class WakelockDiagnosticsUserService : IWakelockDiagnosticsService.Stub() {

    @Throws(RemoteException::class)
    override fun readWakelockCheckin(): String {
        val process = try {
            ProcessBuilder(
                "/system/bin/dumpsys", "-t", DUMPSYS_TIMEOUT_SECONDS.toString(),
                "batterystats", "--charged", "--checkin"
            ).redirectErrorStream(true).start()
        } catch (error: Exception) {
            throw RemoteException("Batterystats komutu başlatılamadı: ${error.message}")
        }

        val outputFuture = CompletableFuture.supplyAsync { readFilteredRows(process) }
        try {
            val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw RemoteException("Batterystats sorgusu zaman aşımına uğradı.")
            }
            if (process.exitValue() != 0) {
                throw RemoteException("Android Batterystats sorgusu başarısız oldu.")
            }
            return outputFuture.get(OUTPUT_COLLECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RemoteException("Wakelock taraması kesildi.")
        } catch (timeout: TimeoutException) {
            process.destroyForcibly()
            throw RemoteException("Batterystats çıktısı zamanında okunamadı.")
        } catch (execution: ExecutionException) {
            val cause = execution.cause
            throw RemoteException("Wakelock çıktısı işlenemedi: ${cause?.message ?: "bilinmeyen hata"}")
        } catch (remote: RemoteException) {
            throw remote
        } catch (error: Exception) {
            throw RemoteException("Wakelock verisi okunamadı: ${error.message}")
        } finally {
            if (process.isAlive) process.destroyForcibly()
            if (!outputFuture.isDone) outputFuture.cancel(true)
        }
    }

    private fun readFilteredRows(process: Process): String {
        val output = StringBuilder()
        var outputBytes = 0
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!BatteryStatsWakelockParser.isWakelockCheckinRow(line)) continue
                val extraBytes = line.toByteArray(StandardCharsets.UTF_8).size + 1
                if (outputBytes + extraBytes > MAX_RETURN_BYTES) {
                    process.destroyForcibly()
                    throw IllegalStateException(
                        "Wakelock verisi çok büyük; tarama güvenli boyut sınırında durduruldu."
                    )
                }
                output.append(line).append('\n')
                outputBytes += extraBytes
            }
        }
        return output.toString()
    }


    /** Stops one explicitly selected third-party package without invoking a shell string. */
    @Throws(RemoteException::class)
    override fun forceStopPackage(packageName: String): Boolean {
        val command = ShizukuPackageCommandFactory.forceStop(packageName) ?: return false
        return runShellCommand(*command.toTypedArray())
    }

    /** Applies Android's RUN_ANY_IN_BACKGROUND app-op restriction for the selected package. */
    @Throws(RemoteException::class)
    override fun restrictBackground(packageName: String): Boolean {
        val command = ShizukuPackageCommandFactory.restrictBackground(packageName) ?: return false
        return runShellCommand(*command.toTypedArray())
    }

    private fun runShellCommand(vararg command: String): Boolean {
        val process = try {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        } catch (error: Exception) {
            throw RemoteException("Shizuku komutu başlatılamadı: ${error.message}")
        }
        try {
            val finished = process.waitFor(SHELL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            // Commands are intentionally invoked as argv, never through `sh -c`.
            return process.exitValue() == 0
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            throw RemoteException("Shizuku komutu kesildi.")
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private companion object {
        const val DUMPSYS_TIMEOUT_SECONDS = 15
        const val PROCESS_TIMEOUT_SECONDS = 18L
        const val OUTPUT_COLLECT_TIMEOUT_SECONDS = 2L
        const val SHELL_COMMAND_TIMEOUT_SECONDS = 8L
        // Keep the Binder response well below Android's transaction buffer limit.
        const val MAX_RETURN_BYTES = 384 * 1024
    }
}
