package com.lamphaus.core.data.cloud

import android.util.Log
import android.os.SystemClock

/**
 * Logcat tracing for every backend call, under the single tag
 * `Lamphaus.Cloud`. Every operation logs three things:
 *
 *   D/Lamphaus.Cloud: pairing.create → label=Living room TV
 *   I/Lamphaus.Cloud: pairing.create ✓ (312ms)
 *   E/Lamphaus.Cloud: pairing.create ✗ (2981ms)
 *   java.net.UnknownHostException: ...
 *
 * Secrets (OTPs, tokens) never reach the log — pass response/request JSON
 * through [sanitize] before printing it.
 */
object CloudLog {

    const val TAG = "Lamphaus.Cloud"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
    }

    /**
     * Times [block] and logs its outcome. Failures are logged with the full
     * stack trace, then rethrown so the caller's Result/error handling stays
     * untouched — this is pure observability.
     */
    suspend fun <T> traced(operation: String, request: String? = null, block: suspend () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        d("$operation →${request?.let { " $it" }.orEmpty()}")
        try {
            val result = block()
            i("$operation ✓ (${SystemClock.elapsedRealtime() - startedAt}ms)")
            return result
        } catch (t: Throwable) {
            e("$operation ✗ (${SystemClock.elapsedRealtime() - startedAt}ms)", t)
            throw t
        }
    }

    /** Same as [traced] for call sites whose contract is a [Result]. */
    suspend fun <T> tracedResult(operation: String, request: String? = null, block: suspend () -> T): Result<T> =
        try {
            Result.success(traced(operation, request, block))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.failure(t)
        }

    private val SECRET_JSON_FIELDS =
        Regex("""("(?:otp|token|hashed_token|access_token|refresh_token|id_token|secret|code_hash)"\s*:\s*")[^"]*(")""")

    private val EMAIL_LOCAL_PART = Regex("""([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@[A-Za-z0-9.-]""")

    /**
     * Masks secret JSON fields (`"otp":"123456"` → `"otp":"••••••"`) and most
     * of an email local part, so responses are safe to paste into chat while
     * still showing shape, statuses and identifiers.
     */
    fun sanitize(payload: String): String {
        val masked = SECRET_JSON_FIELDS.replace(payload) { match ->
            "${match.groupValues[1]}••••••${match.groupValues[2]}"
        }
        return EMAIL_LOCAL_PART.replace(masked) { match ->
            "${match.groupValues[1]}•••@"
        }
    }

    /** Long payloads (full JSON responses) get clamped to keep logcat readable. */
    fun clamp(payload: String, max: Int = 400): String =
        if (payload.length <= max) payload else payload.take(max) + "…(+${payload.length - max} chars)"
}
