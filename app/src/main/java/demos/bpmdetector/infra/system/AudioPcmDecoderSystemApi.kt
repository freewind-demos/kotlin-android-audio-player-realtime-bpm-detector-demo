package demos.bpmdetector.infra.system

import android.content.ContentResolver
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

// SystemApi：只负责把音频文件解码成 mono PCM。
class AudioPcmDecoderSystemApi(
    private val contentResolver: ContentResolver,
) {
    // 顺序解码整首歌，边解码边回调 PCM chunk。
    fun decodeMonoPcm(
        uri: Uri,
        isActive: () -> Boolean = { true },
        onChunkDecoded: (sampleRate: Int, monoSamples: FloatArray) -> Unit,
    ) {
        contentResolver.openFileDescriptor(uri, "r")?.use { fileDescriptor ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(
                    fileDescriptor.fileDescriptor,
                )
                val trackIndex = findAudioTrackIndex(extractor)
                extractor.selectTrack(trackIndex)

                val inputFormat = extractor.getTrackFormat(trackIndex)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                    ?: throw IllegalArgumentException("missing audio mime")
                val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    44_100
                }
                var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    1
                }
                var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(inputFormat, null, null, 0)
                    codec.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    while (!outputDone && isActive()) {
                        if (!inputDone) {
                            val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                    ?: throw IllegalStateException("missing decoder input buffer")
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0,
                                    )
                                    extractor.advance()
                                }
                            }
                        }

                        when (val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val outputFormat = codec.outputFormat
                                if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                }
                                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                }
                            }

                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                            else -> {
                                if (outputBufferIndex >= 0) {
                                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                        ?: throw IllegalStateException("missing decoder output buffer")
                                    if (bufferInfo.size > 0) {
                                        outputBuffer.position(bufferInfo.offset)
                                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                        val chunkBuffer = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                                        val monoSamples = when (pcmEncoding) {
                                            AudioFormat.ENCODING_PCM_FLOAT -> {
                                                decodeFloatMono(chunkBuffer, channelCount)
                                            }

                                            else -> {
                                                decodePcm16Mono(chunkBuffer, channelCount)
                                            }
                                        }
                                        if (monoSamples.isNotEmpty()) {
                                            onChunkDecoded(sampleRate, monoSamples)
                                        }
                                    }
                                    codec.releaseOutputBuffer(outputBufferIndex, false)
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        outputDone = true
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    codec.stop()
                    codec.release()
                }
            } finally {
                extractor.release()
            }
        } ?: throw IllegalArgumentException("unable to open audio file")
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
        for (trackIndex in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return trackIndex
            }
        }
        throw IllegalArgumentException("no audio track found")
    }

    private fun decodePcm16Mono(
        buffer: ByteBuffer,
        channelCount: Int,
    ): FloatArray {
        val safeChannelCount = channelCount.coerceAtLeast(1)
        val shortBuffer = buffer.asShortBuffer()
        val frameCount = shortBuffer.remaining() / safeChannelCount
        if (frameCount <= 0) {
            return FloatArray(0)
        }

        val monoSamples = FloatArray(frameCount)
        for (frameIndex in 0 until frameCount) {
            var sum = 0f
            for (channelIndex in 0 until safeChannelCount) {
                sum += shortBuffer.get() / 32768f
            }
            monoSamples[frameIndex] = sum / safeChannelCount
        }
        return monoSamples
    }

    private fun decodeFloatMono(
        buffer: ByteBuffer,
        channelCount: Int,
    ): FloatArray {
        val safeChannelCount = channelCount.coerceAtLeast(1)
        val floatBuffer = buffer.asFloatBuffer()
        val frameCount = floatBuffer.remaining() / safeChannelCount
        if (frameCount <= 0) {
            return FloatArray(0)
        }

        val monoSamples = FloatArray(frameCount)
        for (frameIndex in 0 until frameCount) {
            var sum = 0f
            for (channelIndex in 0 until safeChannelCount) {
                sum += floatBuffer.get()
            }
            monoSamples[frameIndex] = sum / safeChannelCount
        }
        return monoSamples
    }
}
