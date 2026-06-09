# 🎵 CarMusic — 車用無損音樂播放器

專為車用 Android 主機設計的音樂播放 App，支援 FLAC、WAV 等無損格式，
核心功能為**即時同步動態歌詞**顯示。

---

## 📱 功能特色

| 功能 | 說明 |
|------|------|
| 🎧 無損播放 | FLAC、WAV、MP3、AAC、OGG — ExoPlayer Media3 原生解碼 |
| 🎤 動態歌詞 | 自動從 [LRCLIB](https://lrclib.net) 抓取 LRC 時間軸歌詞 |
| 🖥️ 橫屏車機 UI | 左：封面 + 控制；右：大字歌詞 —— 適合 7–10 吋螢幕 |
| 📂 本地 .lrc | 自動讀取同目錄 `.lrc` 檔案，離線也能顯示歌詞 |
| 💾 歌詞快取 | 下載後快取 30 天，減少流量消耗 |
| ⏭️ 列表播放 | 上/下首、標題/藝術家/專輯排序 |

---

## 🔨 建置方式

### 需求
- **Android Studio** Hedgehog (2023.1.1) 或更新版本
- **JDK 17** 以上
- Android SDK **API 26–34**

### 步驟
```bash
# 1. 解壓縮後進入目錄
cd CarMusicPlayer

# 2. 用 Android Studio 開啟，或直接命令列建置
./gradlew assembleDebug

# 3. 安裝到裝置/模擬器
./gradlew installDebug
```

APK 產出路徑：`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 專案架構

```
CarMusicPlayer/
├── app/src/main/
│   ├── java/com/carmusic/player/
│   │   ├── data/
│   │   │   ├── model/         Song.kt、LrcLine.kt
│   │   │   ├── api/           LyricsApi.kt   ← LRCLIB API
│   │   │   └── repository/    MusicRepository、LyricsRepository
│   │   ├── ui/
│   │   │   ├── library/       LibraryActivity、LibraryViewModel、SongAdapter
│   │   │   └── player/        PlayerActivity、PlayerViewModel、LyricsView、MusicService
│   │   └── util/
│   │       └── LrcParser.kt   ← 解析 LRC 格式
│   └── res/
│       ├── layout/            activity_player.xml、activity_library.xml
│       ├── values/            colors / strings / themes
│       └── drawable/          向量圖示
```

---

## ⚡ 歌詞同步流程

```
playSong()
    ↓
LyricsRepository.getLyrics()
    ├─ 1. 讀本地 .lrc 同名檔案  (最優先)
    ├─ 2. 讀 App 快取目錄
    └─ 3. LRCLIB API (精確) → 模糊搜尋 (備用)
             ↓
         LrcParser.parse()  →  List<LrcLine(timeMs, text)>
             ↓
         PlayerViewModel.lyrics (LiveData)
             ↓
         LyricsView.setLyrics()
             ↓
  每 200ms → LyricsView.updatePosition(positionMs)
             ↓
     Canvas 重繪（當前行放大、動畫滾動）
```

---

## 🛠️ 主要依賴

| 函式庫 | 版本 | 用途 |
|--------|------|------|
| media3-exoplayer | 1.3.1 | 音訊播放（含 FLAC/WAV） |
| media3-session | 1.3.1 | 背景播放通知 |
| OkHttp | 4.12.0 | LRCLIB 歌詞 API |
| Glide | 4.16.0 | 專輯封面載入 |
| Material Components | 1.12.0 | UI 元件 |

---

## 📜 授權

本專案僅供個人學習與車用 Android 開發參考。
歌詞資料來源：[LRCLIB](https://lrclib.net)（開放、免費、無需 API Key）。
