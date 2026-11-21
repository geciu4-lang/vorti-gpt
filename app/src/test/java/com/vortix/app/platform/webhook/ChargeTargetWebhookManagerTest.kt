package com.vortix.app.platform.webhook

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeTargetWebhookManagerTest {
    @Test fun invalidHttpUrlFailsBeforeNetworkConnection() = runTest {
        val result = ChargeTargetWebhookManager().execute(
            "http://192.168.1.20/webhook",
            ChargeTargetWebhookManager.Method.POST,
            test = true
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("HTTPS"))
    }

    @Test fun blankUrlFailsClearly() = runTest {
        val result = ChargeTargetWebhookManager().execute(
            "", ChargeTargetWebhookManager.Method.GET, test = true
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("girilmedi"))
    }
}
