package demos.bpmdetector.domain.handler

import kotlin.math.abs

// 纯算法：把实时波形估算成 BPM。
class BpmEstimator(
    // 估算下限，排除过慢周期。
    private val minBpm: Int = 70,
    // 估算上限，排除过快周期。
    private val maxBpm: Int = 190,
    // 保存最近一段能量窗口，做自相关。
    private val windowCapacity: Int = 512,
) {
    // 滑动窗口里的短时能量序列。
    private val energyWindow = ArrayDeque<Float>()
    // 平滑后的 BPM，减少数字抖动。
    private var smoothedBpm: Float? = null

    // 切歌或停止时清空历史。
    fun reset() {
        energyWindow.clear()
        smoothedBpm = null
    }

    // 输入一帧波形，输出当前估算 BPM。
    fun addWaveformFrame(frame: ByteArray): Int? {
        if (frame.isEmpty()) {
            return null
        }

        // 把 8bit 波形转换成平均能量包络。
        var sum = 0f
        for (sample in frame) {
            sum += abs(sample.toInt() - 128)
        }
        val averageEnergy = sum / frame.size

        // 维护固定长度滑动窗口。
        if (energyWindow.size == windowCapacity) {
            energyWindow.removeFirst()
        }
        energyWindow.addLast(averageEnergy)

        // 样本还不够时，不急着给 BPM。
        if (energyWindow.size < 160) {
            return null
        }

        val energy = energyWindow.toList()
        // Visualizer 回调约 100 次/秒，所以 6000/lag 可换成 BPM。
        val lagMin = (6000f / maxBpm).toInt().coerceAtLeast(4)
        val lagMax = (6000f / minBpm).toInt().coerceAtMost(energy.lastIndex / 2)
        if (lagMax <= lagMin) {
            return null
        }

        // 在 BPM 区间内找自相关峰值最高的周期。
        var bestLag = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in lagMin..lagMax) {
            var score = 0f
            for (index in lag until energy.size) {
                score += energy[index] * energy[index - lag]
            }
            val normalizedScore = score / (energy.size - lag)
            if (normalizedScore > bestScore) {
                bestScore = normalizedScore
                bestLag = lag
            }
        }

        if (bestLag <= 0) {
            return null
        }

        // 把最佳周期换算成 BPM，再做指数平滑。
        val instantBpm = 6000f / bestLag
        smoothedBpm = if (smoothedBpm == null) {
            instantBpm
        } else {
            smoothedBpm!! * 0.82f + instantBpm * 0.18f
        }
        return smoothedBpm!!.toInt()
    }
}
