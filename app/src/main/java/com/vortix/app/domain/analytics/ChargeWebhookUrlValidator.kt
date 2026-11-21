package com.vortix.app.domain.analytics

import java.net.URI

/** HTTPS-only validation prevents sending webhook tokens over an unencrypted network. */
object ChargeWebhookUrlValidator {
    fun validate(url: String): String? {
        val candidate = url.trim()
        if (candidate.isBlank()) return "Webhook URL girilmedi."
        if (candidate.length > 2048) return "Webhook URL çok uzun."
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return "Webhook URL biçimi geçersiz."
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "Güvenlik için yalnızca HTTPS webhook adresleri desteklenir."
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            return "Webhook adresinde geçerli bir sunucu adı bulunmalı; kullanıcı bilgisi veya parça (#) kullanılamaz."
        }
        if (uri.port !in -1..65535) return "Webhook portu geçersiz."
        return null
    }
}
