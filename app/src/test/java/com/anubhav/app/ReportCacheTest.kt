package com.anubhav.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anubhav.app.data.model.CustomerBill
import com.anubhav.app.utils.ReportCache
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cache had no age limit, so a pending bill paid at the clinic counter kept
 * showing as owed until the user happened to tap Refresh. These pin down the
 * expiry rules and the offline fallback that has to survive them.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12 has no image for SDK 36; the app supports 24+ so any modern
// level exercises the same code.
@Config(sdk = [34])
class ReportCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun bill(key: Int, pending: Double) = CustomerBill(
        billKey = key,
        billNo = "ALC$key",
        billDate = "2026-08-18",
        patientName = "Patient $key",
        netAmount = pending,
        receivedAmount = 0.0,
        pendingAmount = pending,
    )

    private fun cacheFile(key: String) =
        File(File(context.filesDir, "report_cache"), "$key.json")

    private fun age(key: String, millis: Long) {
        val file = cacheFile(key)
        assertTrue("expected $key to have been written", file.exists())
        file.setLastModified(System.currentTimeMillis() - millis)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "report_cache").deleteRecursively()
    }

    @Test
    fun missingEntryReadsAsNull() {
        assertNull(ReportCache.readBills(context, "pending_9999999999"))
    }

    @Test
    fun freshBillsAreServedFromCache() {
        ReportCache.writeBills(context, "pending_1", listOf(bill(1, 500.0)))

        val cached = ReportCache.readBills(context, "pending_1")

        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals(500.0, cached?.first()?.pendingAmount ?: 0.0, 0.001)
    }

    @Test
    fun billsOlderThanTheBillingWindowAreTreatedAsAbsent() {
        ReportCache.writeBills(context, "pending_1", listOf(bill(1, 500.0)))
        age("pending_1", ReportCache.BILLING_MAX_AGE + TimeUnit.MINUTES.toMillis(1))

        assertNull("money owed must not be served from a stale cache",
            ReportCache.readBills(context, "pending_1"))
    }

    @Test
    fun staleBillsAreStillAvailableToAnOfflineCaller() {
        ReportCache.writeBills(context, "pending_1", listOf(bill(1, 500.0)))
        age("pending_1", TimeUnit.DAYS.toMillis(30))

        // The AKTIV box is offline 01:00-06:00; a stale answer beats no answer, but only
        // when the caller explicitly asks for one.
        assertNotNull(ReportCache.readBills(context, "pending_1", ReportCache.ANY_AGE))
    }

    @Test
    fun historyKeepsForADayUnlikeBilling() {
        ReportCache.writeBills(context, "bills_1", listOf(bill(1, 0.0)))
        age("bills_1", TimeUnit.HOURS.toMillis(2))

        assertNull("2h is stale for billing", ReportCache.readBills(context, "bills_1"))
        assertNotNull("2h is fresh for history",
            ReportCache.readBills(context, "bills_1", ReportCache.HISTORY_MAX_AGE))
    }

    @Test
    fun aClockThatMovedBackwardsInvalidatesTheEntry() {
        ReportCache.writeBills(context, "pending_1", listOf(bill(1, 500.0)))
        cacheFile("pending_1").setLastModified(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2))

        assertNull("an entry we cannot date must not be trusted",
            ReportCache.readBills(context, "pending_1"))
    }

    @Test
    fun corruptEntryIsDiscardedRatherThanCrashing() {
        ReportCache.writeBills(context, "pending_1", listOf(bill(1, 500.0)))
        cacheFile("pending_1").writeText("{not json")

        assertNull(ReportCache.readBills(context, "pending_1"))
        assertTrue("a corrupt entry should be deleted", !cacheFile("pending_1").exists())
    }

    @Test
    fun payingABillClearsItFromPendingHoweverOldTheEntryIs() {
        ReportCache.writeBills(context, "pending_9", listOf(bill(1, 500.0), bill(2, 250.0)))
        age("pending_9", TimeUnit.DAYS.toMillis(10))

        ReportCache.removePendingBill(context, "9", 1)

        val left = ReportCache.readBills(context, "pending_9", ReportCache.ANY_AGE)
        assertEquals(1, left?.size)
        assertEquals(2, left?.first()?.billKey)
    }

    @Test
    fun theOfflineQueueNeverExpires() {
        // Queued collector patients exist only here until they sync; ageing them out
        // would throw away work done with no signal.
        ReportCache.writeQueuedCollectorPatients(context, "queue_1", emptyList())
        age("queue_1", TimeUnit.DAYS.toMillis(365))

        assertNotNull(ReportCache.readQueuedCollectorPatients(context, "queue_1"))
    }
}
