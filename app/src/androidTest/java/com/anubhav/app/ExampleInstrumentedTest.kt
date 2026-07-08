package com.anubhav.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anubhav.app.utils.CustomerSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        CustomerSessionManager.clear(appContext)
    }

    @Test
    fun packageAndLauncherAreRenamed() {
        assertEquals("com.anubhav.app", appContext.packageName)

        val launcher = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        assertEquals(MainActivity::class.java.name, launcher?.component?.className)
    }

    @Test
    fun customerSessionRequiresSavedFirebaseUid() {
        CustomerSessionManager.clear(appContext)
        assertFalse(CustomerSessionManager.isLoggedIn(appContext))

        CustomerSessionManager.save(
            context = appContext,
            phone = "9230755875",
            email = "patient@example.com",
            name = "Patient",
            firebaseUid = "firebase-uid",
        )

        assertTrue(CustomerSessionManager.isLoggedIn(appContext))
        assertEquals("9230755875", CustomerSessionManager.getPhone(appContext))
    }
}
