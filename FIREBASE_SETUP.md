# Firebase Setup for SecuPhone

This guide will help you set up Firebase Authentication for the SecuPhone application.

## Prerequisites

1. A Google account
2. Android Studio installed
3. SecuPhone project cloned and opened in Android Studio

## Firebase Setup Steps

### 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" and follow the prompts to create a new Firebase project
3. Enter a project name (e.g., "SecuPhone")
4. Choose whether to enable Google Analytics (recommended)
5. Accept the terms and click "Create project"

### 2. Register Your Android App with Firebase

1. In the Firebase console, click on your newly created project
2. Click the Android icon (</>) to add an Android app
3. Enter the package name: `com.example.secuphone_bycoursor`
4. Enter an app nickname (optional, e.g., "SecuPhone")
5. Enter the SHA-1 debug signing certificate (optional for authentication, but recommended)
   - To get the SHA-1, run this command in your project directory: 
     ```
     ./gradlew signingReport
     ```
   - Look for the "SHA1" value under "Task :app:signingReport"
6. Click "Register app"

### 3. Download and Add Configuration File

1. Download the `google-services.json` file
2. Place the file in the `app/` directory of your SecuPhone project

### 4. Enable Authentication Methods

1. In the Firebase console, go to "Authentication" from the left menu
2. Click on "Get started" or "Sign-in method" tab
3. Enable the "Email/Password" sign-in method
4. (Optional) Enable other sign-in methods as needed
5. Save your changes

### 5. Update Rules for Firestore (if using)

1. In the Firebase console, go to "Firestore Database" from the left menu
2. Click on "Rules" tab
3. Update the rules to allow authenticated access:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

4. Click "Publish"

## Using the Template File

If you prefer, you can manually edit the configuration:

1. Rename `app/google-services.json.template` to `app/google-services.json`
2. Open the file and replace the placeholder values with your actual Firebase project values:
   - `YOUR_PROJECT_NUMBER` - Found in Project Settings > General
   - `YOUR_PROJECT_ID` - Found in Project Settings > General
   - `YOUR_STORAGE_BUCKET` - Found in Project Settings > General
   - `YOUR_MOBILESDK_APP_ID` - Found in Project Settings > General > Your Apps
   - `YOUR_API_KEY` - Found in Project Settings > General > Your Apps

## Troubleshooting

- If you encounter build errors after adding Firebase, try the following:
  - Sync your project with Gradle files
  - Clean and rebuild the project
  - Ensure the `google-services.json` file is correctly placed in the `app/` directory
  - Verify that the package name in the Firebase console matches your app's package name

- If authentication is not working:
  - Check if Email/Password authentication is enabled in the Firebase console
  - Verify that your app has internet permission in the Android manifest
  - Check Logcat for specific error messages

## Additional Resources

- [Firebase Authentication Documentation](https://firebase.google.com/docs/auth)
- [Add Firebase to Android Project](https://firebase.google.com/docs/android/setup)
- [Firebase Authentication on Android](https://firebase.google.com/docs/auth/android/start)

# Multi-Device Tracking Feature

For the multi-device tracking feature, the app uses the following Firebase Realtime Database structure:

```
signals/
└── [user-uid]/
    └── [device-id]/
        ├── latitude: double
        ├── longitude: double
        ├── last_seen: timestamp
        └── device_name: string
```

Each device uploads its real-time location to the signals path under the user's UID. This allows all devices associated with the same UID to view each other's locations.

## Firebase Security Rules

The following security rules should be applied to ensure proper access control:

```json
{
  "rules": {
    "signals": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",
        "devices": {
          "$deviceId": {
            ".read": "auth != null",
            ".write": "auth != null && auth.uid == $uid"
          }
        }
      }
    },
    "commands": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    }
  }
}
```

These rules ensure that:
- Only authenticated users can read or write to their own signals
- Users can only read and write to their own user data
- Device information can be read by any authenticated user but only written by the device owner 