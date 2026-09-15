package com.lamphaus.core.player

import com.lamphaus.core.model.SubtitleCue
import com.lamphaus.core.model.SubtitleCharset
import com.lamphaus.core.model.SubtitleCueParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fetches a text sidecar subtitle with its per-source headers (plan §4),
 * retries once safely, detects charset, and parses cues. Errors surface as
 * null so the timing panel can offer Retry without crashing playback; URLs
 * and headers are never logged (SHR-PROD-06).
 *
 * Cancellation cancels the underlying OkHttp call instead of waiting for the
 * read timeout, the decoded download is capped before parsing, and parsing
 * runs on a CPU dispatcher, never Main (PERF-10). One shared client serves
 * every track change; per-item clients are never created.
 */
class SidecarSubtitleLoader(
    private val headerSource: (url: String) -> Map<String, String> = { PlaybackHeaderRegistry.get(it) },
    private val client: OkHttpClient = sharedClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxBytes: Int = DEFAULT_MAX_SUBTITLE_BYTES,
) {

    suspend fun load(url: String): List<SubtitleCue>? {
        val bytes = fetchOrNull(url) ?: run {
            // One safe retry, but never after cancellation.
            currentCoroutineContext().ensureActive()
            fetchOrNull(url)
        } ?: return null
        return withContext(cpuDispatcher) {
            SubtitleCueParser.parse(SubtitleCharset.decode(bytes)).takeIf(List<SubtitleCue>::isNotEmpty)
        }
    }

    /**
     * Returns the decoded bytes, or null for any recoverable failure
     * (non-2xx, oversized, empty, IO error). Cancellation always propagates.
     */
    private suspend fun fetchOrNull(url: String): ByteArray? {
        val request = Request.Builder().url(url).apply {
            headerSource(url).forEach { (name, value) -> header(name, value) }
        }.build()
        return try {
            withContext(ioDispatcher) {
                client.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    body.byteStream().use { stream -> readBounded(stream) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    /** Reads at most [maxBytes]; an oversized sidecar is a recoverable failure, not a truncation. */
    private fun readBounded(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            if (output.size() + read > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val DEFAULT_MAX_SUBTITLE_BYTES = 4 * 1024 * 1024

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** Suspends for the response and cancels the call when the waiter is cancelled. */
@OptIn(InternalCoroutinesApi::class)
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            val token = continuation.tryResumeWithException(e)
            if (token != null) continuation.completeResume(token)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.close()
                return
            }
            val token = continuation.tryResume(response)
            if (token != null) {
                continuation.completeResume(token)
            } else {
                response.close()
            }
        }
    })
}
