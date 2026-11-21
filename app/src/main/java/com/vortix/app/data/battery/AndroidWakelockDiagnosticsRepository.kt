package com.vortix.app.data.battery

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.vortix.app.BuildConfig
import com.vortix.app.data.battery.shizuku.IWakelockDiagnosticsService
import com.vortix.app.data.battery.shizuku.WakelockDiagnosticsUserService
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockDiagnosticsResult
import com.vortix.app.domain.repository.WakelockDiagnosticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

@Singleton
class AndroidWakelockDiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : WakelockDiagnosticsRepository {

    private val serviceMutex = Mutex()

    @Volatile
    private var remoteService: IWakelockDiagnosticsService? = null
    @Volatile
    private var serviceConnection: ServiceConnection? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, WakelockDiagnosticsUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("wakelock")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    override fun currentAccessState(): WakelockAccessState {
        return try {
            if (!Shizuku.pingBinder()) return WakelockAccessState.SHIZUKU_NOT_RUNNING
            if (Shizuku.isPreV11() || Shizuku.getVersion() < MIN_SHIZUKU_PERMISSION_API_VERSION) {
                return WakelockAccessState.UNSUPPORTED_VERSION
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                WakelockAccessState.READY
            } else {
                WakelockAccessState.PERMISSION_REQUIRED
            }
        } catch (_: SecurityException) {
            WakelockAccessState.PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            WakelockAccessState.SHIZUKU_NOT_RUNNING
        }
    }

    override fun requestPermission(): Boolean {
        if (currentAccessState() != WakelockAccessState.PERMISSION_REQUIRED) return false
        return runCatching {
            Shizuku.requestPermission(REQUEST_CODE_WAKELOCK_PERMISSION)
            true
        }.getOrDefault(false)
    }

    override suspend fun readSinceLastCharge(): WakelockDiagnosticsResult {
        val access = currentAccessState()
        if (access != WakelockAccessState.READY) {
            return WakelockDiagnosticsResult.Unavailable(access)
        }

        return withContext(Dispatchers.IO) {
            try {
                val service = getOrBindUserService()
                val rawRows = service.readWakelockCheckin()
                val packageManager = context.packageManager
                val records = BatteryStatsWakelockParser.parse(rawRows).map { record ->
                    val packages = runCatching {
                        packageManager.getPackagesForUid(record.uid)?.toList().orEmpty()
                    }.getOrDefault(emptyList())
                    record.copy(packageNames = packages)
                }
                WakelockDiagnosticsResult.Success(
                    records = records,
                    capturedAtEpochMillis = System.currentTimeMillis()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                WakelockDiagnosticsResult.Failure(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "Wakelock verisi okunamadı. Shizuku/Sui bağlantısını ve iznini kontrol et."
                )
            }
        }
    }

    private suspend fun getOrBindUserService(): IWakelockDiagnosticsService = serviceMutex.withLock {
        remoteService?.let { existing ->
            if (runCatching { existing.asBinder().isBinderAlive }.getOrDefault(false)) {
                return@withLock existing
            }
        }

        serviceConnection?.let { oldConnection ->
            withContext(Dispatchers.Main.immediate) {
                runCatching { Shizuku.unbindUserService(userServiceArgs, oldConnection, true) }
            }
        }
        remoteService = null
        serviceConnection = null

        val connected = CompletableDeferred<IWakelockDiagnosticsService>()
        val connectionReference = AtomicReference<ServiceConnection?>()
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (serviceConnection !== connectionReference.get()) return
                val remote = binder?.let { IWakelockDiagnosticsService.Stub.asInterface(it) }
                if (remote == null) {
                    connected.completeExceptionally(IllegalStateException("Shizuku UserService geçerli Binder döndürmedi."))
                } else {
                    remoteService = remote
                    connected.complete(remote)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (serviceConnection !== connectionReference.get()) return
                remoteService = null
                if (!connected.isCompleted) {
                    connected.completeExceptionally(IllegalStateException("Shizuku UserService bağlantısı kesildi."))
                }
            }
        }
        connectionReference.set(newConnection)
        serviceConnection = newConnection

        try {
            withContext(Dispatchers.Main.immediate) {
                Shizuku.bindUserService(userServiceArgs, newConnection)
            }
            withTimeoutOrNull(USER_SERVICE_CONNECT_TIMEOUT_MILLIS) { connected.await() }
                ?: throw IllegalStateException("Shizuku UserService bağlantısı zaman aşımına uğradı.")
        } catch (cancelled: CancellationException) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { Shizuku.unbindUserService(userServiceArgs, newConnection, true) }
            }
            serviceConnection = null
            remoteService = null
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.Main.immediate) {
                runCatching { Shizuku.unbindUserService(userServiceArgs, newConnection, true) }
            }
            serviceConnection = null
            remoteService = null
            throw error
        }
    }

    private companion object {
        const val MIN_SHIZUKU_PERMISSION_API_VERSION = 11
        const val REQUEST_CODE_WAKELOCK_PERMISSION = 7302
        const val USER_SERVICE_CONNECT_TIMEOUT_MILLIS = 20_000L
    }
}
