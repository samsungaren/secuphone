# Quick Fix Guide for Remote Lock Function

If your remote lock function isn't working because the Firebase Realtime Database isn't properly set up, follow these steps to get it working quickly:

## 1. Check Firebase Setup

### Verify Firebase Configuration
1. Make sure you have the `google-services.json` file in your app directory
2. If not, download it from the Firebase Console and add it to your project

### Add Required Dependencies
Add these to your app-level `build.gradle`:

```gradle
// Firebase dependencies
implementation platform('com.google.firebase:firebase-bom:32.2.0')
implementation 'com.google.firebase:firebase-database'
implementation 'com.google.firebase:firebase-auth'
```

## 2. Create the Realtime Database

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on "Realtime Database" in the left sidebar
4. Click "Create Database"
5. Choose a location (select the one closest to you)
6. Start in **test mode** for now
7. Click "Enable"

## 3. Test User Authentication

The remote lock function requires a logged-in user. Add this code to check authentication:

```java
// Add to your activity's onCreate method
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
if (user == null) {
    // User is not logged in, show login screen or create a test user
    FirebaseAuth.getInstance().signInAnonymously()
        .addOnSuccessListener(authResult -> {
            Log.d("Auth", "Anonymous sign-in successful");
            // Now try to use the remote lock function
        })
        .addOnFailureListener(e -> {
            Log.e("Auth", "Anonymous sign-in failed", e);
        });
} else {
    Log.d("Auth", "User already signed in: " + user.getUid());
    // User is already signed in, proceed with remote lock
}
```

## 4. Test Database Connection

Add this code to verify your database connection:

```java
// Add to your activity's onCreate method
DatabaseReference testRef = FirebaseDatabase.getInstance().getReference(".info/connected");
testRef.addValueEventListener(new ValueEventListener() {
    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {
        boolean connected = snapshot.getValue(Boolean.class);
        Log.d("Firebase", "Connected to Firebase: " + connected);
        Toast.makeText(getApplicationContext(), 
                      "Firebase connection: " + (connected ? "Connected" : "Disconnected"), 
                      Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
        Log.e("Firebase", "Connection listener cancelled", error.toException());
    }
});
```

## 5. Test Device Registration

Test if your device registration is working:

```java
// Add to your activity after authentication is confirmed
DeviceRegistrationManager deviceManager = DeviceRegistrationManager.getInstance(this);
deviceManager.registerDevice(task -> {
    if (task.isSuccessful()) {
        Log.d("DeviceReg", "Device registered successfully");
        Toast.makeText(this, "Device registered with Firebase", Toast.LENGTH_SHORT).show();
    } else {
        Log.e("DeviceReg", "Device registration failed", task.getException());
        Toast.makeText(this, "Failed to register device: " + 
                      task.getException().getMessage(), Toast.LENGTH_LONG).show();
    }
});
```

## 6. Test Remote Lock Command

To test if the remote lock command works:

```java
// This simulates sending a lock command to the current device
String deviceId = Settings.Secure.getString(
    getContentResolver(), Settings.Secure.ANDROID_ID);
    
RemoteLockManager lockManager = RemoteLockManager.getInstance(this);
lockManager.sendLockCommand(deviceId, "Test lock message", task -> {
    if (task.isSuccessful()) {
        Log.d("RemoteLock", "Lock command sent successfully");
        Toast.makeText(this, "Lock command sent", Toast.LENGTH_SHORT).show();
    } else {
        Log.e("RemoteLock", "Failed to send lock command", task.getException());
        Toast.makeText(this, "Failed to send lock command: " + 
                      task.getException().getMessage(), Toast.LENGTH_LONG).show();
    }
});
```

## 7. Check Common Issues

If you're still having problems:

1. **Internet Permissions**: Make sure you have in your AndroidManifest.xml:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```

2. **Firebase Instance**: Ensure you're using the same Firebase instance throughout your app

3. **Database Rules**: If you're in production, make sure your security rules allow read/write access

4. **Device Admin Permissions**: For the lock function to work, device admin must be enabled:
   ```java
   // Check if device admin is active
   DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
   ComponentName adminComponent = new ComponentName(this, RemoteLockDeviceAdmin.class);
   boolean isAdmin = dpm.isAdminActive(adminComponent);
   Log.d("Admin", "Device admin active: " + isAdmin);
   ```

5. **Logging**: Add more logging to track the execution flow:
   ```java
   // Enable verbose Firebase logging
   FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
   ```

## 8. Quick Fix for Common Errors

If you're getting specific errors:

- **"Permission denied"**: Your database rules are too restrictive
- **"Authentication failed"**: User is not logged in or token expired
- **"Database not found"**: You haven't created the Realtime Database yet
- **"Network error"**: Check internet connection and firewall settings

By following these steps, you should be able to get your remote lock function working with Firebase Realtime Database. 