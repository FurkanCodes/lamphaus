package com.lamphaus.core.player.mpv

import java.util.concurrent.atomic.AtomicReference

/**
 * JNI bridge to the dlopen-based libmpv glue (`liblamphaus_mpv.so`).
 * Absent libmpv: every call degrades to no-op/false and [availability]
 * reports [Availability.UNAVAILABLE], keeping Media3 primary (plan §1).
 *
 * MPV is single-threaded per handle: all calls here are serialized through
 * [lock] by [MpvPlayer] on its event and command paths.
 *
 * Provider stream URLs and headers pass through this class only as mpv
 * arguments — never logged, never echoed (SHR-PROD-06).
 */
object MpvLibrary {
    enum class Availability { AVAILABLE, GLUE_UNAVAILABLE, LIBMPV_UNAVAILABLE }

    private val loaded = AtomicReference<Availability?>(null)

    val availability: Availability
        get() = loaded.updateAndGet { current -> current ?: probe() } ?: Availability.GLUE_UNAVAILABLE

    private fun probe(): Availability = try {
        System.loadLibrary("lamphaus_mpv")
        val handle = nativeCreate()
        if (handle != 0L) {
            // Created a probe handle; libmpv is present. Teardown immediately.
            nativeDestroy(handle)
            Availability.AVAILABLE
        } else {
            Availability.LIBMPV_UNAVAILABLE
        }
    } catch (_: UnsatisfiedLinkError) {
        Availability.GLUE_UNAVAILABLE
    } catch (_: Throwable) {
        Availability.LIBMPV_UNAVAILABLE
    }

    @JvmStatic
    private external fun nativeCreate(): Long

    @JvmStatic
    private external fun nativeInitialize(handle: Long): Boolean

    @JvmStatic
    private external fun nativeDestroy(handle: Long)

    @JvmStatic
    private external fun nativeSetOptionString(handle: Long, name: String, value: String?): Boolean

    @JvmStatic
    private external fun nativeSetPropertyString(handle: Long, name: String, value: String?): Boolean

    @JvmStatic
    private external fun nativeGetPropertyString(handle: Long, name: String): String?

    @JvmStatic
    private external fun nativeCommand(handle: Long, args: Array<String>): Boolean

    @JvmStatic
    private external fun nativeObserveProperty(handle: Long, name: String): Boolean

    /** Returns the next event id, or 0 on timeout. */
    @JvmStatic
    private external fun nativeWaitEvent(handle: Long, timeoutSeconds: Double): Int

    @JvmStatic
    private external fun nativeWakeup(handle: Long)

    @JvmStatic
    private external fun nativeAttachSurface(handle: Long, surface: Any): Boolean

    @JvmStatic
    private external fun nativeDetachSurface(handle: Long): Boolean

    // ── Wrapped surface for MpvPlayer (keeps lock discipline in one place) ──

    fun isAvailable(): Boolean = availability == Availability.AVAILABLE

    fun create(): Long = synchronized(lock) { nativeCreate() }

    fun initialize(handle: Long): Boolean = synchronized(lock) { nativeInitialize(handle) }

    fun destroy(handle: Long) = synchronized(lock) { nativeDestroy(handle) }

    fun setOptionString(handle: Long, name: String, value: String?): Boolean =
        synchronized(lock) { nativeSetOptionString(handle, name, value) }

    fun setPropertyString(handle: Long, name: String, value: String?): Boolean =
        synchronized(lock) { nativeSetPropertyString(handle, name, value) }

    fun getPropertyString(handle: Long, name: String): String? =
        synchronized(lock) { nativeGetPropertyString(handle, name) }

    fun command(handle: Long, args: List<String>): Boolean =
        synchronized(lock) { nativeCommand(handle, args.toTypedArray()) }

    fun observeProperty(handle: Long, name: String): Boolean =
        synchronized(lock) { nativeObserveProperty(handle, name) }

    /** [timeoutSeconds] 0.0 polls without blocking; >0 sleeps until an event. */
    fun waitEvent(handle: Long, timeoutSeconds: Double): Int =
        synchronized(lock) { nativeWaitEvent(handle, timeoutSeconds) }

    fun wakeup(handle: Long) = synchronized(lock) { nativeWakeup(handle) }

    fun attachSurface(handle: Long, surface: Any): Boolean =
        synchronized(lock) { nativeAttachSurface(handle, surface) }

    fun detachSurface(handle: Long): Boolean = synchronized(lock) { nativeDetachSurface(handle) }

    internal val lock = Any()
}
