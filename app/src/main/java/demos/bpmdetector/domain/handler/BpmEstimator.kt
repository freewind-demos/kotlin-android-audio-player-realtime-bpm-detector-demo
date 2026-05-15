package demos.bpmdetector.domain.handler

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

// BPM + 首拍偏移。
data class BpmEstimate(
    val bpm: Int,
    val beatOffsetMs: Int,
)

// 纯算法：把整首歌的 PCM 包络估算成 BPM。
class BpmEstimator(
    // 估算下限，排除过慢周期。
    private val minBpm: Int = 70,
    // 估算上限，排除过快周期。
    private val maxBpm: Int = 190,
    // 短时能量窗口，提取节奏包络。
    private val frameSize: Int = 1024,
    // 相邻窗口的滑动步长。
    private val hopSize: Int = 512,
    // 能量平滑系数，弱化持续音量，突出拍点起伏。
    private val energySmoothing: Float = 0.85f,
) {
    // 整首歌的 onset 包络。
    private val onsetEnvelope = ArrayList<Float>()
    // 采样率由解码器提供。
    private var sampleRate: Int = 0
    // 分块解码时缓存未处理样本。
    private var pendingSamples = FloatArray(frameSize * 4)
    private var pendingSize: Int = 0
    // 平滑能量基线，做简单 onset detection。
    private var smoothedEnergy: Float = 0f
    private var hasSmoothedEnergy: Boolean = false

    // 新文件分析前清空历史。
    fun reset() {
        onsetEnvelope.clear()
        sampleRate = 0
        pendingSize = 0
        smoothedEnergy = 0f
        hasSmoothedEnergy = false
    }

    // 由解码器注入 PCM 采样率。
    fun setSampleRate(sampleRate: Int) {
        if (sampleRate > 0) {
            this.sampleRate = sampleRate
        }
    }

    // 连续喂入 mono PCM。
    fun ingest(samples: FloatArray) {
        if (samples.isEmpty()) {
            return
        }

        ensurePendingCapacity(pendingSize + samples.size)
        System.arraycopy(samples, 0, pendingSamples, pendingSize, samples.size)
        pendingSize += samples.size

        while (pendingSize >= frameSize) {
            consumeFrame()
        }
    }

    // 文件喂完后输出最终 BPM。
    fun finish(): BpmEstimate? {
        if (sampleRate <= 0 || onsetEnvelope.size < 12) {
            return null
        }

        val envelope = FloatArray(onsetEnvelope.size) { onsetEnvelope[it] }
        val mean = envelope.average().toFloat()
        val centered = FloatArray(envelope.size)
        var variance = 0f
        for (index in envelope.indices) {
            val centeredValue = envelope[index] - mean
            centered[index] = centeredValue
            variance += centeredValue * centeredValue
        }
        if (variance <= 0f) {
            return null
        }

        val lagMin = ceil(60f * sampleRate / (hopSize * maxBpm)).toInt().coerceAtLeast(1)
        val lagMax = floor(60f * sampleRate / (hopSize * minBpm)).toInt()
            .coerceAtMost(centered.lastIndex)
        if (lagMax <= lagMin) {
            return null
        }

        var bestLag = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in lagMin..lagMax) {
            val span = centered.size - lag
            if (span < 8) {
                continue
            }

            var numerator = 0f
            var leftEnergy = 0f
            var rightEnergy = 0f
            for (index in 0 until span) {
                val left = centered[index]
                val right = centered[index + lag]
                numerator += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }

            val denominator = sqrt(leftEnergy * rightEnergy)
            if (denominator <= 0f) {
                continue
            }

            val normalizedScore = numerator / denominator
            if (normalizedScore > bestScore) {
                bestScore = normalizedScore
                bestLag = lag
            }
        }

        if (bestLag <= 0) {
            return null
        }

        val bestPhase = findBestPhase(bestLag)
        val bpm = (60f * sampleRate / (hopSize * bestLag)).toInt()
        val beatOffsetMs = (bestPhase * hopSize * 1000f / sampleRate).toInt()
        return BpmEstimate(
            bpm = bpm,
            beatOffsetMs = beatOffsetMs,
        )
    }

    private fun findBestPhase(lag: Int): Int {
        var bestPhase = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (phase in 0 until lag) {
            var score = 0f
            var count = 0
            var index = phase
            while (index < onsetEnvelope.size) {
                score += onsetEnvelope[index]
                count++
                index += lag
            }
            if (count == 0) {
                continue
            }
            val normalizedScore = score / count
            if (normalizedScore > bestScore) {
                bestScore = normalizedScore
                bestPhase = phase
            }
        }
        return bestPhase
    }

    private fun consumeFrame() {
        var sumSquares = 0f
        for (index in 0 until frameSize) {
            val sample = pendingSamples[index]
            sumSquares += sample * sample
        }
        val rmsEnergy = sqrt(sumSquares / frameSize)
        val onset = if (hasSmoothedEnergy) {
            max(0f, rmsEnergy - smoothedEnergy)
        } else {
            0f
        }
        onsetEnvelope.add(onset)
        smoothedEnergy = if (hasSmoothedEnergy) {
            smoothedEnergy * energySmoothing + rmsEnergy * (1f - energySmoothing)
        } else {
            rmsEnergy
        }
        hasSmoothedEnergy = true

        val remainingSamples = pendingSize - hopSize
        if (remainingSamples > 0) {
            System.arraycopy(pendingSamples, hopSize, pendingSamples, 0, remainingSamples)
        }
        pendingSize = remainingSamples
    }

    private fun ensurePendingCapacity(requiredSize: Int) {
        if (requiredSize <= pendingSamples.size) {
            return
        }

        var newCapacity = pendingSamples.size
        while (newCapacity < requiredSize) {
            newCapacity *= 2
        }
        pendingSamples = pendingSamples.copyOf(newCapacity)
    }
}
