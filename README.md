# BookReader — Free PDF Reader with Cross-Device Sync

A free, open-source Android PDF reader that syncs your reading position between your Samsung phone and tablet (or any Android devices) using Firebase.

## Features

- **PDF Library** — import any PDF from device storage; books are copied into private app storage
- **Smooth PDF rendering** — powered by PdfiumAndroid via the `barteksc` viewer
- **Cross-device sync** — reading progress (page number) is saved to Firebase Firestore in real-time
- **Auto-resume** — opening a book on your tablet picks up exactly where you left on your phone
- **Dark mode** — follows system theme
- **Tablet-friendly** — landscape support, keep-screen-on while reading
- **Offline-first** — works without internet; syncs when connection is available
- **Free forever** — no subscription, no ads

## Setup

### 1. Firebase (required for sync)

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a project → Add Android app → package name: `com.bookreader`
3. Download `google-services.json` → replace `app/google-services.json`
4. Enable **Google Sign-in**: Authentication → Sign-in method → Google
5. Create **Firestore** database with these security rules:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/books/{bookId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

6. Copy your **Web Client ID** from Authentication → Sign-in method → Google → expand → Web SDK configuration
7. Paste it into `app/src/main/res/values/strings.xml` as `default_web_client_id`

### 2. Build

```bash
./gradlew assembleDebug
```

Install the APK on both your phone and tablet, sign in with the same Google account, and your reading progress will sync automatically.

## How sync works

1. When you open a book, the app fetches the latest page from Firebase
2. If the remote page is ahead of the local page, it resumes from there
3. While reading, progress is pushed to Firebase ~1.5 seconds after a page turn
4. If you switch devices mid-session, the new device prompts you to jump to the other device's page
5. On app pause/background, progress is saved immediately

## Tech stack

- Kotlin, AndroidX, Material3
- `com.github.barteksc:android-pdf-viewer` (PDF rendering)
- Firebase Auth + Firestore (sync)
- Room (local library)
- Coroutines + Flow
