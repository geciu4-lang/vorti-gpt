package com.vortix.app.domain.analytics

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChargeWebhookUrlValidatorTest {
    @Test fun acceptsHttpsWebhookUrl() {
        assertNull(ChargeWebhookUrlValidator.validate("https://home.example.com/api/webhook/test"))
    }

    @Test fun rejectsPlainHttp() {
        assertNotNull(ChargeWebhookUrlValidator.validate("http://192.168.1.10/webhook"))
    }

    @Test fun rejectsMissingHost() {
        assertNotNull(ChargeWebhookUrlValidator.validate("https:///webhook"))
    }

    @Test fun rejectsUserInfoAndFragments() {
        assertNotNull(ChargeWebhookUrlValidator.validate("https://user:pass@example.com/hook"))
        assertNotNull(ChargeWebhookUrlValidator.validate("https://example.com/hook#token"))
    }

    @Test fun rejectsBlankAndOverlongUrls() {
        assertNotNull(ChargeWebhookUrlValidator.validate(" "))
        assertNotNull(ChargeWebhookUrlValidator.validate("https://a.com/" + "x".repeat(2050)))
    }
}
