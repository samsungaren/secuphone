# Firebase Realtime Database Setup Guide for SecuPhone

This guide will help you set up Firebase Realtime Database for your SecuPhone app to enable the remote lock functionality.

## 1. Firebase Project Setup

### Create a Firebase Project
1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" and follow the setup wizard
3. Enter a project name (e.g., "SecuPhone")
4. Enable Google Analytics if desired
5. Click "Create project"

### Add Android App to Firebase Project
1. In the Firebase console, click on the Android icon
2. Enter your app's package name: `com.example.secuphone`
3. Enter a nickname (optional)
4. Register the app

### Download and Add Configuration File
1. Download the `google-services.json` file
2. Place it in your app's root directory
3. Make sure your app-level `build.gradle` file includes:
   ```gradle
   dependencies {
       // Add the Google services Gradle plugin
       implementation platform('com.google.firebase:firebase-bom:32.2.0')
       implementation 'com.google.firebase:firebase-database'
       implementation 'com.google.firebase:firebase-auth'
       // Other dependencies...
   }
   ```
4. Make sure your project-level `build.gradle` file includes:
   ```gradle
   buildscript {
       dependencies {
           classpath 'com.google.gms:google-services:4.3.15'
           // Other classpath dependencies...
       }
   }
   ```
5. Apply the Google services plugin in your app-level `build.gradle`:
   ```gradle
   apply plugin: 'com.google.gms.google-services'
   ```

## 2. Enable and Configure Realtime Database

### Create a Realtime Database
1. In the Firebase console, navigate to "Realtime Database" from the left sidebar
2. Click "Create Database"
3. Choose a location (select the one closest to your users)
4. Start in **test mode** for development (we'll secure it later)
5. Click "Enable"

### Set Up Security Rules
Once your app is working, update the security rules:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "commands": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "lock_status": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

## 3. Enable Authentication

1. In the Firebase console, navigate to "Authentication"
2. Click "Get started"
3. Enable Email/Password authentication
4. (Optional) Enable other authentication methods as needed

## 4. Database Structure

Your app uses the following structure:

```
/users/{uid}/devices/{device_id}
    - model: "Pixel 6"
    - manufacturer: "Google"
    - name: "pixel"
    - last_seen: 1627984561234
    - online: true

/commands/{uid}/devices/{device_id}
    - lock: true/false
    - message: "This device was locked remotely"
    - sender: "{sender_device_id}"

/lock_status/{uid}/{device_id}
    - command_sent: true/false
    - locked: true/false
```

## 5. Testing Firebase Connection

Add this code to your main activity to verify the Firebase connection:

```java
DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
connectedRef.addValueEventListener(new ValueEventListener() {
    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {
        boolean connected = snapshot.getValue(Boolean.class);
        if (connected) {
            Log.d("Firebase", "Connected to Firebase");
            Toast.makeText(MainActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("Firebase", "Disconnected from Firebase");
            Toast.makeText(MainActivity.this, "Disconnected from Firebase", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
        Log.e("Firebase", "Listener was cancelled");
    }
});
```

## 6. Common Issues and Solutions

### Authentication Issues
- Make sure a user is logged in before trying to access the database
- Check that Firebase Auth is properly initialized
- Verify that your app has internet permission in the manifest

### Database Access Issues
- Check your security rules to ensure they allow the operations you're trying to perform
- Verify the paths you're using match the structure defined above
- Make sure you're using the correct user ID in paths

### Connection Issues
- Ensure your device has internet connectivity
- Check that the `google-services.json` file is correctly placed
- Verify that your app has the necessary permissions in the manifest:
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  ```

### Device Registration Issues
- If `DeviceRegistrationManager.registerDevice()` fails, check if the user is logged in
- Ensure the device ID is being generated correctly
- Verify that the database paths are correct

## 7. Debugging Tips

### Enable Detailed Logging
```java
FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
```

### Monitor Database in Real-time
Use the Firebase Console to watch database changes in real-time as you test your app.

### Check Authentication State
```java
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
if (user != null) {
    Log.d("Auth", "User is logged in: " + user.getUid());
} else {
    Log.d("Auth", "No user is logged in");
}
```

## 8. Next Steps

1. Implement user authentication in your app if not already done
2. Test device registration with multiple devices
3. Test the remote lock functionality between devices
4. Monitor the database structure in the Firebase Console
5. Implement proper error handling for network issues
6. Update security rules for production

By following this guide, you should have a working Firebase Realtime Database setup for your SecuPhone app's remote lock functionality. 