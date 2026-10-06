package com.lamphaus.core.player

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.source.SampleQueue
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.NativeBuffers
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PLY-NET-01: samples written to off-heap allocations read back intact (QA-01, QA-08). */
@RunWith(AndroidJUnit4::class)
class NativeSampleBufferTest {

    @Test
    fun samplesRoundTripThroughOffHeapAllocations() {
        assertTrue("native buffers must load on device", NativeBuffers.isAvailable())
        val allocator = DefaultAllocator(true, NativeMemoryPolicy.SEGMENT_BYTES, 0, true)
        val queue = SampleQueue.createWithoutDrm(allocator)
        queue.format(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build())

        // One sample written from an array, one through a DataReader, both spanning segments.
        val first = Random(1).nextBytes(150_000)
        val second = Random(2).nextBytes(210_000)
        queue.sampleData(ParsableByteArray(first), first.size)
        queue.sampleMetadata(0L, C.BUFFER_FLAG_KEY_FRAME, first.size, 0, null)
        var offset = 0
        val reader = DataReader { target, targetOffset, length ->
            val count = minOf(length, second.size - offset, 7_000)
            if (count == 0) C.RESULT_END_OF_INPUT else count.also {
                System.arraycopy(second, offset, target, targetOffset, it)
                offset += it
            }
        }
        var written = 0
        while (written < second.size) written += queue.sampleData(reader, second.size - written, false)
        queue.sampleMetadata(40_000L, 0, second.size, 0, null)
        assertTrue(NativeBuffers.getAllocatedBytes() >= first.size + second.size)

        val holder = FormatHolder()
        val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
        assertEquals(C.RESULT_FORMAT_READ, queue.read(holder, buffer, 0, false))
        listOf(first, second).forEach { expected ->
            buffer.clear()
            assertEquals(C.RESULT_BUFFER_READ, queue.read(holder, buffer, 0, false))
            buffer.flip()
            val actual = ByteArray(buffer.data!!.remaining()).also { buffer.data!!.get(it) }
            assertArrayEquals(expected, actual)
        }
        queue.release()
        allocator.reset()
    }
}
