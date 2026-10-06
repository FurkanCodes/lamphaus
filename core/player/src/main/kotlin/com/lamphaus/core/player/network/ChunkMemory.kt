package com.lamphaus.core.player.network

import java.io.IOException

/** Memory for one download chunk. Writes and reads address bytes from the chunk's start. */
abstract class ChunkBuffer(val capacity: Int) {
    abstract fun write(at: Int, source: ByteArray, offset: Int, length: Int)

    abstract fun read(at: Int, target: ByteArray, offset: Int, length: Int)
}

/**
 * Hands out chunk buffers of one size. Released buffers are kept for reuse,
 * up to the session's chunk cap, so a playback does not reallocate a 16 MiB
 * buffer for every chunk.
 */
abstract class ChunkMemory(private val keep: Int) {
    private val idle = ArrayDeque<ChunkBuffer>()

    protected abstract fun allocate(capacity: Int): ChunkBuffer

    protected open fun free(buffer: ChunkBuffer) = Unit

    @Throws(IOException::class)
    fun obtain(capacity: Int): ChunkBuffer {
        synchronized(idle) {
            val reused = idle.indexOfFirst { it.capacity == capacity }
            if (reused >= 0) return idle.removeAt(reused)
        }
        return try {
            allocate(capacity)
        } catch (error: OutOfMemoryError) {
            drain()
            throw IOException("Chunk buffer allocation failed", error)
        }
    }

    fun recycle(buffer: ChunkBuffer) {
        val dropped = synchronized(idle) {
            if (idle.size < keep) {
                idle.addLast(buffer)
                null
            } else {
                buffer
            }
        }
        dropped?.let(::free)
    }

    /** Frees every idle buffer, e.g. when the session ends or memory runs short. */
    fun drain() {
        val freed = synchronized(idle) { idle.toList().also { idle.clear() } }
        freed.forEach(::free)
    }
}

/** Java-heap chunks: used unless "Native memory buffer" is on. */
class HeapChunkMemory(keep: Int) : ChunkMemory(keep) {
    override fun allocate(capacity: Int): ChunkBuffer = HeapChunkBuffer(capacity)
}

internal class HeapChunkBuffer(capacity: Int) : ChunkBuffer(capacity) {
    private val bytes = ByteArray(capacity)

    override fun write(at: Int, source: ByteArray, offset: Int, length: Int) =
        System.arraycopy(source, offset, bytes, at, length)

    override fun read(at: Int, target: ByteArray, offset: Int, length: Int) =
        System.arraycopy(bytes, at, target, offset, length)
}

/**
 * Off-heap chunks for "Native memory buffer" (PLY-NET-01), from the same
 * native allocator as ExoPlayer's sample buffers. A failed allocation falls
 * back to the heap; frees are deferred by the allocator.
 */
@androidx.media3.common.util.UnstableApi
class NativeChunkMemory(keep: Int) : ChunkMemory(keep) {
    override fun allocate(capacity: Int): ChunkBuffer =
        androidx.media3.exoplayer.upstream.NativeBuffers.allocate(capacity)?.let(::NativeChunkBuffer)
            ?: HeapChunkBuffer(capacity)

    override fun free(buffer: ChunkBuffer) {
        if (buffer is NativeChunkBuffer) androidx.media3.exoplayer.upstream.NativeBuffers.free(buffer.memory)
    }
}

private class NativeChunkBuffer(val memory: java.nio.ByteBuffer) : ChunkBuffer(memory.capacity()) {
    override fun write(at: Int, source: ByteArray, offset: Int, length: Int) {
        val view = memory.duplicate()
        view.position(at)
        view.put(source, offset, length)
    }

    override fun read(at: Int, target: ByteArray, offset: Int, length: Int) {
        val view = memory.duplicate()
        view.position(at)
        view.get(target, offset, length)
    }
}
