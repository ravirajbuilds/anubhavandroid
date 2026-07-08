package com.anubhav.app

import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun launcherPackageWasRenamed() {
        assertFalse(BuildConfig.APPLICATION_ID.startsWith("com.example"))
    }
}
