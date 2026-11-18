# 隨身聽 Pocket Player

給跑步運動用的 Android 音樂播放器：YouTube 網址 / 搜尋、MP3、MP4，**縮小或關螢幕也繼續播**。

## 下載
到 [Releases](../../releases/latest) 下載 `pocket-player.apk` 安裝（每次 push 會由 GitHub Actions 自動編譯）。

## 功能
- **YouTube**：貼上影片或播放清單網址、從 YouTube App「分享 → 隨身聽」直接加入
- **搜尋**：類型（歌曲 / MV / 影片 / 歌單）、長度篩選、運動快選（跑步歌單、160/170/180 BPM、超慢跑 180 BPM、Workout Mix…），點一下立即播放、＋ 加入清單
- **本機檔案**：MP3、MP4、M4A、FLAC… 影片會顯示畫面
- **背景播放**：Media3 前景服務 + 通知列 / 鎖定畫面 / 藍牙耳機按鍵控制
- **跑步不斷線**
  - 自動挑最高音質音軌（通常 Opus 160kbps）
  - 大緩衝：一首歌盡量整首先載進來
  - 邊播邊存 1.5GB 快取，並自動預載接下來 2 首
  - 選單「離線下載整個清單」：出發前先下載，沒訊號也能聽
  - 斷網時不停止，每 3 秒重試，網路一回來自動接著播
  - 串流網址過期自動重新解析，從原位置續播
- **其他**：拔耳機自動暫停、運動 App 語音播報時自動降低音量、記住清單與播放位置

## 參考的開源專案
- [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)：YouTube 解析與搜尋（直接引用）
- [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)：Downloader 實作方式
- [OuterTune/OuterTune](https://github.com/OuterTune/OuterTune)（InnerTune 分支）：快取 + ResolvingDataSource 的播放架構

## 小提醒
部分手機（小米、OPPO、華為等）省電很兇，第一次使用請到右上角選單 →「允許背景播放」。
