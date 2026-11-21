package com.vortix.app.platform.webhook

import com.vortix.app.domain.analytics.ChargeWebhookUrlValidator
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Singleton
class ChargeTargetWebhookManager @Inject constructor() {
    enum class Method { GET, POST }
    data class Result(val success: Boolean, val statusCode: Int?, val message: String)

    suspend fun execute(
        url: String,
        method: Method,
        test: Boolean = false,
        batteryLevelPercent: Int? = null,
        targetPercent: Int? = null
    ): Result {
        ChargeWebhookUrlValidator.validate(url)?.let { return Result(false, null, it) }
        return withContext(Dispatchers.IO) {
            var connection: HttpsURLConnection? = null
            try {
                connection = (URL(url.trim()).openConnection() as HttpsURLConnection).apply {
                    requestMethod = method.name
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("User-Agent", "VORTIX-Android")
                    if (method == Method.POST) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
                if (method == Method.POST) {
                    val event = if (test) "vortix_webhook_test" else "vortix_charge_target_reached"
                    val payload = buildString {
                        append("{\"source\":\"VORTIX\",\"event\":\"").append(event).append('"')
                        if (!test && batteryLevelPercent != null) append(",\"battery_level_percent\":").append(batteryLevelPercent.coerceIn(0, 100))
                        if (!test && targetPercent != null) append(",\"target_percent\":").append(targetPercent.coerceIn(70, 100))
                        append('}')
                    }
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val success = status in 200..299
                Result(
                    success,
                    status,
                    when {
                        test && success -> "Webhook test isteği başarılı (HTTP $status)."
                        success -> "Şarj hedefi webhook isteği başarılı (HTTP $status)."
                        else -> "Webhook sunucusu HTTP $status yanıtı verdi."
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result(false, null, "Webhook gönderilemedi: ${error.message?.take(160) ?: "bağlantı hatası"}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}
