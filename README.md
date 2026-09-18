# Torrent HTTP Downloader

A modern Android application designed to bridge the gap between BitTorrent and HTTP downloading. It supports both **Real-Debrid** for high-speed cloud-assisted downloads and **local BitTorrent** downloading via `libtorrent4j`.

## 🚀 Features

- **Hybrid Torrent Support**: Choose between cloud-based downloading via **Real-Debrid** or local downloading using the integrated **libtorrent4j** engine.
- **Real-Debrid Integration**: Seamlessly connect your Real-Debrid account to unrestrict torrents and generate high-speed HTTP download links.
- **Local BitTorrent Engine**: Download torrents directly on your device with full control over the BitTorrent protocol.
- **Local HTTP Downloader**: Integrated download manager for downloading files from Real-Debrid directly to your device with support for parallel downloads.
- **RSS Feed Support**: Add and manage RSS feeds to easily discover and download new content.
- **Modern Jetpack Compose UI**: A clean, responsive interface built with the latest Android UI toolkit, featuring Material 3 design.
- **Background Downloading**: Robust background service to ensure your downloads continue even when the app is in the background.
- **Torrent Management**: View, add, and manage your torrent tasks with a detailed state machine tracking progress from upload to HTTP download completion.
- **Flexible Storage**: Configure download locations and manage files directly within the app.

## 🛠️ Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3.
- **Navigation**: [Navigation 3](https://developer.android.com/jetpack/androidx/releases/navigation) for modern screen orchestration.
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/) for API interactions.
- **Torrent Logic**: [libtorrent4j](https://github.com/aldenml/libtorrent4j) for local torrent handling.
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for reliable background execution.
- **Concurrency**: Kotlin Coroutines and Flow for reactive data handling.

## 📥 Installation

### Direct APK
You can download the latest APK from the [Releases](https://github.com/felixbrucker/torrent-http-downloader-app/releases) page.

### 🔄 Automatic Updates via Obtainium

For the best experience, we recommend using **[Obtainium](https://github.com/ImranR98/Obtainium)** to install and keep the app updated automatically.

1. Install **Obtainium** on your Android device.
2. Open Obtainium and tap **"Add App"**.
3. Paste this repository's URL: `https://github.com/felixbrucker/torrent-http-downloader-app`
4. Obtainium will notify you and help you install updates automatically whenever a new build is available on GitHub.

## 🏗️ Development

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17+
- A Real-Debrid API Key (required when using real-debrid provider)

## ⚖️ License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
