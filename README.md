# LAN-cam App

LAN-cam App is an open-source Android application designed for monitoring multiple RTSP cameras on local networks. It supports both mobile and TV interfaces, providing a flexible way to keep an eye on your security cameras or baby monitors.

## Features
- **Multi-Camera Live View**: Monitor multiple streams simultaneously.
- **RTSP Support**: Compatible with a wide range of network cameras.
- **TV & Mobile Compatibility**: Optimized UI for both smartphones and Android TV.
- **16 KB Page Size Support**: Fully compliant with Android 15+ requirements.

## Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room (for camera management)
- **Streaming Engine**: [libVLC for Android](https://code.videolan.org/videolan/vlc-android)
- **Protocols**: RTSP (Real Time Streaming Protocol)

## Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35+
- A network camera supporting RTSP

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/lan-cam-app.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Run the app on an emulator or physical device.

## Contributing
We welcome contributions! Please see our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started.

## Roadmap
Check out [ISSUES.md](ISSUES.md) for a list of planned features and areas for improvement.

## License
This project is licensed under the [MIT License](LICENSE) (or your preferred license).
