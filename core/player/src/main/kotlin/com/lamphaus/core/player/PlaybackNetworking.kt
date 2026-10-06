package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.net.SocketTimeoutException
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * The media network stack (QA-08). One process-wide client so warm
 * connections survive seeks, subtitle fetches, and the next episode instead
 * of paying a fresh TLS handshake on every range request.
 */
@UnstableApi
internal object PlaybackNetworking {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Over HTTP/2 the video, its post-seek range requests, and sidecar
            // subtitles share one TCP connection and its flow-control window,
            // so one slow stream can starve the others. HTTP/1.1 gives every
            // load its own connection, the way dedicated players fetch media.
            .protocols(listOf(Protocol.HTTP_1_1))
            // Media3's OkHttp source enqueues its calls, so OkHttp's default of
            // five per host would queue parallel range downloads behind each other.
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 32 })
            .dns(Ipv4FirstDns)
            // Room for every warm range connection, so none is evicted mid-playback.
            .connectionPool(ConnectionPool(32, 3, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * No factory-level User-Agent: OkHttpDataSource would add it next to an
     * add-on's own, so [withDefaultUserAgent] fills it in per request instead.
     */
    fun httpDataSourceFactory(): HttpDataSource.Factory = OkHttpDataSource.Factory(client)

    /** Adds the browser User-Agent unless the add-on supplied one (header names are case-insensitive). */
    fun withDefaultUserAgent(headers: Map<String, String>): Map<String, String> =
        if (headers.keys.any { it.equals(USER_AGENT_HEADER, ignoreCase = true) }) {
            headers
        } else {
            headers + (USER_AGENT_HEADER to DEFAULT_USER_AGENT)
        }

    private const val USER_AGENT_HEADER = "User-Agent"

    /** The browser identity streaming hosts expect; some throttle or refuse unknown clients. */
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

/**
 * Many home networks advertise IPv6 that does not reach the CDN. OkHttp 4
 * tries addresses in order without a fast fallback, so a dead IPv6 route costs
 * a full connect timeout per connection. IPv4 goes first; IPv6 remains the
 * fallback.
 */
private object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
}

/**
 * Keeps transient network failures inside the engine: a failed read is
 * retried with backoff while the buffer keeps playing, so a brief outage never
 * reaches the viewer as an error (SHR-PROD-04). See [StreamingPolicy].
 */
@UnstableApi
internal class StreamingLoadErrorPolicy :
    DefaultLoadErrorHandlingPolicy(StreamingPolicy.MIN_LOADABLE_RETRY_COUNT) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val causes = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }.toList()
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        // Parser, cleartext, and position errors stay fatal as Media3 decides.
        val mediaDelay = super.getRetryDelayMsFor(loadErrorInfo).takeUnless { it == C.TIME_UNSET }
        return StreamingPolicy.retryDelayMillis(
            errorCount = loadErrorInfo.errorCount,
            httpStatus = status,
            timedOut = causes.any { it is SocketTimeoutException },
            mediaDelayMillis = mediaDelay,
        ) ?: C.TIME_UNSET
    }
}
