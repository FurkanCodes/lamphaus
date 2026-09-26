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
import okhttp3.ConnectionPool
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
            .dns(Ipv4FirstDns)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun httpDataSourceFactory(): HttpDataSource.Factory =
        OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)

    private const val USER_AGENT = "Lamphaus/1.0"
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
        // Parser, cleartext, and position errors stay fatal as Media3 decides.
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        val status = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
        return StreamingPolicy.retryDelayMillis(loadErrorInfo.errorCount, status) ?: C.TIME_UNSET
    }
}
