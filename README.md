# kotlin-android-audio-player-realtime-bpm-detector-demo

演示 Android 本地音频播放器，同时不依赖 MP3 metadata，直接监听播放波形，实时估算当前拍速 BPM，并在屏幕中央大字显示。

## 快速开始

### 环境要求

- Android Studio / Android SDK
- Java 17

### 运行

```bash
cd /Volumes/SN550-2T/freewind-demos/kotlin-android-audio-player-realtime-bpm-detector-demo
./gradlew assembleDebug
```

用 Android Studio 打开此目录也可直接运行。

## 注意事项

- BPM 是实时估算值，不是文件标签值。
- 当前算法适合节拍清晰的流行、电子、鼓点明显音频。
- 若歌曲前奏弱、rubato、多变速，前几秒可能不稳定，随后会收敛。

## 教程

### 关键概念

这个 Demo 不读取 ID3、BPM tag、文件名等元信息，只做两件事：

1. `MediaPlayer` 真正播放音频
2. `Visualizer` 抓播放 session 的实时波形

波形进入 `BpmEstimator` 后，会先转成短时能量，再对能量序列做自相关，找出最可能的周期，最后换算成 BPM。

### Demo 原理

分层按 `Entry -> Handler -> Store`：

1. `MainActivity`
   - 负责按钮点击、文件选择、订阅 UI 状态
2. `PlayerHandler`
   - 编排选歌、加载、播放、暂停、停止、错误处理
3. `PlayerStore`
   - 只存 UI 共享状态
4. `AudioPlayerSystemApi`
   - 封装 `MediaPlayer` 与 `Visualizer`
5. `BpmEstimator`
   - 纯算法，不依赖 Android UI

### 关键代码解读

1. `AudioPlayerSystemApi.attachVisualizer()`
   - 把 `Visualizer` 绑到 `MediaPlayer` 的 `audioSessionId`
   - 持续拿到 `waveform` 字节流
2. `BpmEstimator.addWaveformFrame()`
   - 每帧算平均能量
   - 存入滑动窗口
   - 在限定 BPM 区间内遍历延迟值，求最高自相关
   - 用指数平滑减少抖动
3. `activity_main.xml`
   - 中央大号文本专门显示 BPM
   - 下方按钮做播放器控制

## 操作

1. 打开 App
2. 点“选择音频”
3. 选一首本地歌曲
4. 点“播放”
5. 看中间大字 BPM 实时变化
