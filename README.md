# UPI Segregator (Native Android)

A modern, native Android application designed to help users track whom they pay, how much they pay, and analyze their spending patterns.

---

## Architecture & Technology Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3 Design System
- **Architecture**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow
- **Navigation**: Jetpack Navigation Compose
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Build System**: Gradle 8.7 with Android Gradle Plugin 8.5.2 and Version Catalog (`gradle/libs.versions.toml`)
- **Target Android Version**: `minSdk 26` (Android 8.0 Oreo), `targetSdk 34` (Android 14)

---

## Design System

- **Background**: Crisp white (`#FFFFFF`) with subtle surface cards (`#F8FAFC`)
- **Primary Brand Color**: Vivid Blue (`#1E60F6`)
- **Demo Mode Accent**: High-visibility Green (`#22C55E`)
- **Restrained Accents**: Purple (`#7C3AED`) and Teal (`#0D9488`)
- **Corner Radii**: Generous 14dp rounded buttons and input fields

---

## Milestone 1 Status (Current)

- [x] Modern Android Gradle scaffold with Version Catalog (`libs.versions.toml`)
- [x] Android Manifest with `<queries>` for UPI package discovery (`upi://pay`)
- [x] Application entry point and edge-to-edge `MainActivity`
- [x] Material 3 design system (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`)
- [x] Reusable UI components (`AppTextField`, `PrimaryButton`, `SecondaryButton`, `DemoModeBanner`, `DemoEntryCard`)
- [x] Reactive MVVM `AuthViewModel` with input validation and prototype state
- [x] Login and Signup screens with polished UI
- [x] Functional local Demo Mode navigation to placeholder home dashboard
- [x] Git initialized with sensitive files and build artifacts excluded via `.gitignore`

---

## Beginner-Friendly Setup Guide

You do **not** need to install a standalone Kotlin compiler or manually configure environment variables to build and run this project. Android Studio manages everything automatically.

### 1. Install Android Studio
1. Download **Android Studio** (latest version, e.g., Ladybug or Koala) from [developer.android.com/studio](https://developer.android.com/studio).
2. Run the installer using the standard settings.

### 2. JDK Configuration (No need to uninstall Java 8)
- Android Studio comes bundled with its own compatible JDK (JetBrains Runtime JDK 17/21).
- It will automatically use this bundled JDK to build the project. Your existing Java 8 on Windows PATH can remain untouched and will not interfere with Android Studio.

### 3. Install the Android SDK
During the initial Android Studio setup wizard (or inside **Settings > Languages & Frameworks > Android SDK**):
- Under **SDK Platforms**: Check **Android 14.0 ("UpsideDownCake") - API Level 34**.
- Under **SDK Tools**:
  - Android SDK Build-Tools (version 34.x)
  - Android SDK Platform-Tools
  - Android Emulator

### 4. Create an Android Emulator (Virtual Device)
1. In Android Studio, open the **Device Manager** (top-right toolbar icon or under **Tools > Device Manager**).
2. Click **Create Device**.
3. Select **Phone** > **Pixel 7** (or **Pixel 8**).
4. Select the system image: **API 34 (UpsideDownCake)** (x86_64, with Google APIs).
5. Click **Finish**.

### 5. Open and Run the Project
1. Launch Android Studio.
2. Select **Open** and choose this project folder:
   ```
   E:\Studies\Wed Development Course\WEB DEV Projects\UPI Segregator
   ```
3. Android Studio will automatically download Gradle 8.7, synchronize project dependencies, and index the codebase.
4. Select your virtual device in the device dropdown at the top.
5. Click the green **Run (▶)** button or press `Shift + F10`.
6. The app will build and launch on your emulator, displaying the Login screen with one-tap Demo Mode entry.

---

## Connecting to Your GitHub Account (Manual Steps)

Follow these exact steps to connect this local repository to your GitHub account:

### Step 1: Create the repository on GitHub
1. Open your web browser and navigate to [github.com/new](https://github.com/new).
2. Set the **Repository name** to: `upi-segregator`.
3. Choose **Private** (recommended) or **Public**.
4. **IMPORTANT**: Leave "Add a README file", "Add .gitignore", and "Choose a license" **UNCHECKED** (we have already created them locally).
5. Click **Create repository**.

### Step 2: Connect and push from your terminal
Open PowerShell in this project directory:
```powershell
# 1. Connect your local repository to your new GitHub repository
# (Replace YOUR_GITHUB_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/upi-segregator.git

# 2. Ensure your branch is named main
git branch -M main

# 3. Push your initial commit to GitHub
git push -u origin main
```
*Note: When you run `git push`, Git Credential Manager will open a browser window to sign in to GitHub if required.*

### Step 3: Commands for future updates
Whenever new features or milestones are implemented:
```powershell
git add .
git commit -m "Describe your changes"
git push
```

---

## Security Invariants

- **Zero Credential Storage**: This application never requests, handles, or persists UPI PINs, bank account passwords, or OTPs.
- **Local Demo Isolation**: The demo mode operates entirely offline with simulated sample data.
