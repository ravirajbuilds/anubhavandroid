package com.anubhav.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches a report PDF ONLY when the patient taps "View Report".
 * The server URL renders the whole bill into ONE collated PDF (multiple reports → one file),
 * which we download via the Cloudflare tunnel, cache on the phone, and open.
 * A cached copy is reused (works offline / when the clinic server is down 1–6 AM).
 */
object ReportFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun cachedFile(context: Context, billKey: Int): File {
        val dir = File(context.filesDir, "saved_reports").apply { mkdirs() }
        return File(dir, "bill_$billKey.pdf")
    }

    fun isCached(context: Context, billKey: Int): Boolean =
        cachedFile(context, billKey).let { it.exists() && it.length() > 0L }

    /** Download+cache the collated PDF if not already cached. Returns the local file. */
    suspend fun download(context: Context, billKey: Int, viewUrl: String): File =
        withContext(Dispatchers.IO) {
            val file = cachedFile(context, billKey)
            if (file.exists() && file.length() > 0L) return@withContext file
            val req = Request.Builder().url(viewUrl).header("Accept", "application/pdf").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty response")
                val tmp = File(file.parentFile, "${file.name}.part")
                tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                if (tmp.length() == 0L) { tmp.delete(); error("empty PDF") }
                tmp.renameTo(file)
            }
            file
        }

    fun open(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
