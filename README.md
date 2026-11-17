# K.A.M.R.A. — Kier's Amazing Memory Recording App

A desktop Java camera application built with **Java Swing**, **OpenCV**, and **FFmpeg (via JavaCV)** that captures photos and records videos in real time with a custom retro camcorder filter and baked-in On-Screen Display (OSD) overlays.

---

## Features

- **Real-Time Camcorder Filter**
  - Procedural film grain and static noise applied per frame via byte-level pixel manipulation (`DataBufferByte`).
  - Vintage color grading with subtle green/blue tint shifts to emulate late-90s and early-2000s consumer camcorders.
- **Baked-In On-Screen Display (OSD)**
  - **Bottom-Left Timecode:** Live date (`MMM. dd yyyy`) and 12-hour clock (`hh:mm:ss a`) rendered in bold monospace typography with drop shadows.
  - **Bottom-Right Duration Counter:** High-visibility red elapsed recording timer (`HH:MM:SS`).
  - **Top-Right Status Indicator:** Flashing red `REC ●` indicator during active recording sessions and `STBY` when idle.
- **Photo & Video Capture**
  - **Take Photo:** Captures the current filtered frame with the OSD burned in and saves it as a `.png` file.
  - **Record Video:** Encodes live filtered frames directly into H.264 `.mp4` videos at 30 fps using `libx264`.
- **Interactive Side Gallery**
  - Automatically generates thumbnails for captured photos and videos inside a scrollable sidebar.
  - Click any gallery item to open it directly in your system's default media viewer.
  - All media files are organized automatically inside a local `kamra_media/` directory.

---

## Tech Stack

| Component | Technology | Role |
| :--- | :--- | :--- |
| **Language** | Java 17+ | Core application logic and multithreaded frame loop |
| **UI Framework** | Java Swing / AWT (`KamraUI`) | Desktop window, control buttons, and scrolling gallery sidebar |
| **Camera Capture** | JavaCV (`OpenCVFrameGrabber`) | Built-in webcam hardware interfacing |
| **Filter & HUD** | `BufferedImage` & `Graphics2D` | Byte-level pixel noise, color tinting, and OSD text rendering |
| **Video Encoding** | JavaCV (`FFmpegFrameRecorder`) | Real-time H.264 (`libx264`) `.mp4` encoding |
| **Build Tool** | Apache Maven | Dependency management for Windows x86_64 native binaries |

---

## Prerequisites

- **Java Development Kit (JDK) 17** or newer
- **Apache Maven** (bundled with IDEs like Apache NetBeans, IntelliJ IDEA, or Eclipse)
- A built-in or USB webcam
- **Windows Camera Permissions:** Ensure **Settings > Privacy & security > Camera > Let desktop apps access your camera** is turned **On**.

---

## Getting Started

### 1. Clone the Repository
```bash
git clone [https://github.com/ZielVer/KAMRA.git](https://github.com/ZielVer/KAMRA.git)
cd KAMRA
```

### 2. Build the Project
Maven is configured with `<javacpp.platform>windows-x86_64<javacpp.platform>` so it fetches only the required 64-bit Windows OpenCV and FFmpeg binaries.
``` bash
mvn clean compile
```
*(If using **Apache NetBeans**, open the project folder, right-click **KAMRA**, and select **Clean and Build**.)*

### 3. Run the Application
Run the main Swing interface class:
``` bash
mvn exec:java -Dexec.mainClass="com.mycompany.kamra.KamraUI"
```
*(In NetBeans, open `KamraUI.java` and press Shift + F6.)*

## Project Structure
``` text
KAMRA/
├── pom.xml                                      # Maven configuration & JavaCV Windows dependencies
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── mycompany/
│                   └── kamra/
│                       └── KamraUI.java         # Main Swing GUI, camera thread, filter, & gallery
└── kamra_media/                                 # Auto-generated folder for saved .png and .mp4 files
```

## Usage
1. Launch `KamraUI` to start the live webcam preview.
2. Click **Take Photo** to save a timestamped snapshot (`KAMRA_IMG_YYYYMMDD_HHMMSS.png`).
3. Click **Start Recording** to begin recording video (`KAMRA_VID_YYYYYMMDD_HHMMSS.mp4`). Click **Stop Recording** to finalize and save the file.
4. Click any thumbnail in the **GALLERY** sidebar on the right to view your saved photos or play back recorded videos.

5. ## Author
6. **Kier Tan**
