# kotlin-android-audio-player-realtime-bpm-detector-demo

演示 Android 本地音频播放器，同时不依赖 MP3 metadata，直接解码整首音频做离线 BPM 分析，并在屏幕中央大字显示。

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

- BPM 是离线估算值，不是文件标签值。
- 当前算法适合节拍清晰的流行、电子、鼓点明显音频。
- 若歌曲前奏弱、rubato、多变速，可能会出现半拍/倍拍误判。

## 教程

### 关键概念

这个 Demo 不读取 ID3、BPM tag、文件名等元信息，只做三件事：

1. `MediaPlayer` 真正播放音频
2. `MediaExtractor + MediaCodec` 把文件解码成 PCM
3. `BpmEstimator` 对整首 PCM 的 onset 包络做自相关

PCM 进入 `BpmEstimator` 后，会先转成短时能量与 onset 包络，再对包络做归一化自相关，找出最可能的周期，最后换算成 BPM。

### Demo 原理

分层按 `Entry -> Handler -> Store`：

1. `MainActivity`
   - 负责按钮点击、文件选择、订阅 UI 状态
2. `PlayerHandler`
   - 编排选歌、加载、播放、暂停、停止、离线分析、错误处理
3. `PlayerStore`
   - 只存 UI 共享状态
4. `AudioPlayerSystemApi`
   - 封装 `MediaPlayer`
5. `BpmEstimator`
   - 纯算法，不依赖 Android UI
6. `AudioPcmDecoderSystemApi`
   - 封装 `MediaExtractor + MediaCodec`

### 关键代码解读

1. `AudioPcmDecoderSystemApi.decodeMonoPcm()`
   - 顺序解码整首歌
   - 统一下混成 mono PCM
2. `BpmEstimator`
   - 分帧计算 RMS 能量
   - 做简单 onset detection
   - 在限定 BPM 区间内遍历延迟值，求最高归一化自相关
3. `activity_main.xml`
   - 中央大号文本专门显示 BPM
   - 下方按钮做播放器控制

## 操作

1. 打开 App
2. 点“选择音频”
3. 选一首本地歌曲
4. 等几秒让离线分析完成
5. 点“播放”
6. 看中间大字 BPM
