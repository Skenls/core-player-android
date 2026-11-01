package com.coreplayer.app.player

import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OpusSeekingTest {

    @Before
    fun setUp() {
        mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { android.net.Uri.fromFile(any()) } returns mockUri
        every { android.net.Uri.parse(any()) } returns mockUri
    }

    @Test
    fun testOggExtractor_withUnknownLength_isUnseekable() {
        val file = File("/tmp/test_opus.ogg")
        if (!file.exists()) return

        val bytes = file.readBytes()
        val dataSource = ByteArrayDataSource(bytes)
        dataSource.open(DataSpec(android.net.Uri.fromFile(file), 0, -1L))

        // input length is -1 (C.LENGTH_UNSET), exactly as returned by OkHttp when streaming on-the-fly!
        val input = DefaultExtractorInput(dataSource, 0, -1L)
        val extractor = OggExtractor()

        val capturedSeekMap = slot<SeekMap>()
        val output = mockk<ExtractorOutput>(relaxed = true)
        every { output.seekMap(capture(capturedSeekMap)) } returns Unit

        extractor.init(output)
        val posHolder = PositionHolder()
        while (!capturedSeekMap.isCaptured) {
            val res = extractor.read(input, posHolder)
            if (res == Extractor.RESULT_SEEK || res == Extractor.RESULT_END_OF_INPUT) {
                break
            }
        }

        assertTrue(capturedSeekMap.isCaptured)
        println("Unknown length SeekMap: ${capturedSeekMap.captured}, isSeekable = ${capturedSeekMap.captured.isSeekable}")
        assertFalse(capturedSeekMap.captured.isSeekable)
    }

    @Test
    fun testOggExtractor_withKnownLength_isSeekable() {
        val file = File("/tmp/test_opus.ogg")
        if (!file.exists()) return

        val bytes = file.readBytes()
        val dataSource = ByteArrayDataSource(bytes)
        dataSource.open(DataSpec(android.net.Uri.fromFile(file), 0, bytes.size.toLong()))

        // input length is known (from Content-Length header)
        val input = DefaultExtractorInput(dataSource, 0, bytes.size.toLong())
        val extractor = OggExtractor()

        val capturedSeekMap = slot<SeekMap>()
        val output = mockk<ExtractorOutput>(relaxed = true)
        every { output.seekMap(capture(capturedSeekMap)) } returns Unit

        extractor.init(output)
        val posHolder = PositionHolder()
        var currentInput = input
        while (!capturedSeekMap.isCaptured) {
            val res = extractor.read(currentInput, posHolder)
            if (res == Extractor.RESULT_SEEK) {
                // Extractor requested seek (e.g. Seeking to end of stream to find duration/granule)
                dataSource.close()
                dataSource.open(DataSpec(android.net.Uri.fromFile(file), posHolder.position, bytes.size.toLong() - posHolder.position))
                currentInput = DefaultExtractorInput(dataSource, posHolder.position, bytes.size.toLong())
                continue
            }
            if (res == Extractor.RESULT_END_OF_INPUT) {
                break
            }
        }

        assertTrue(capturedSeekMap.isCaptured)
        println("Known length SeekMap: ${capturedSeekMap.captured}, isSeekable = ${capturedSeekMap.captured.isSeekable}")
        assertTrue(capturedSeekMap.captured.isSeekable)
    }
}
