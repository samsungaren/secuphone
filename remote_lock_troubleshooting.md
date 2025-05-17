# Remote Lock Troubleshooting Guide

This guide helps you troubleshoot common issues with the Remote Lock functionality in SecuPhone.

## Prerequisites Check

Before troubleshooting, ensure that:

1. You have created a Firebase project and added the app to it
2. You have downloaded and added the `google-services.json` file to your app directory
3. You have created a Firebase Realtime Database (URL: https://secuphone-f2660-default-rtdb.firebaseio.com/)
4. You have enabled Authentication in Firebase
5. You are logged into the app with a valid user account

## Common Issues and Solutions

### 1. Remote Lock Commands Not Sending

**Symptoms:**
- No response when trying to lock a device
- No error messages
- Target device doesn't receive lock command

**Possible Causes and Solutions:**

a) **User Not Authenticated**
   - Check if user is logged in using `FirebaseAuth.getInstance().getCurrentUser()`
   - If null, the user needs to authenticate first
   - Solution: Implement proper login flow or use anonymous authentication for testing

b) **Database Path Issues**
   - Verify that the database paths are correct in `RemoteLockManager.sendLockCommand()`
   - Solution: Check that you're using the correct structure:
     ```
     /commands/{uid}/devices/{device_id}/lock
     ```

c) **Network Connectivity**
   - Check if the device has internet access
   - Solution: Add connectivity checks before sending commands

d) **Firebase Database Rules**
   - Restrictive rules might block write operations
   - Solution: Update rules to allow authenticated users to write to their paths:
     ```json
     {
       "rules": {
         "commands": {
           "$uid": {
             ".read": "$uid === auth.uid",
             ".write": "$uid === auth.uid"
           }
         }
       }
     }
     ```

### 2. Remote Lock Service Not Receiving Commands

**Symptoms:**
- Commands are sent successfully (confirmed in Firebase console)
- Target device doesn't lock
- No error logs in the service

**Possible Causes and Solutions:**

a) **Service Not Running**
   - Check if `RemoteLockService` is running in the background
   - Solution: Ensure service is started properly in `RemoteLockDeviceAdmin.onEnabled()`
   - Use `adb shell dumpsys activity services` to check running services

b) **Firebase Listeners Not Set Up**
   - Check if listeners are properly registered in `startListeningForLockCommands()`
   - Solution: Verify that all three listeners are set up:
     - General lock command listener
     - Device-specific lock command listener
     - Last lock target listener

c) **Device ID Mismatch**
   - The device ID used for sending commands might not match the one used for listening
   - Solution: Use consistent device ID generation across the app:
     ```java
     String deviceId = Settings.Secure.getString(
         context.getContentResolver(), Settings.Secure.ANDROID_ID);
     ```

d) **Battery Optimization**
   - Battery optimization might be killing the service
   - Solution: Request battery optimization exemption:
     ```java
     Intent intent = new Intent();
     intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
     intent.setData(Uri.parse("package:" + getPackageName()));
     startActivity(intent);
     ```

### 3. Device Not Locking Despite Receiving Commands

**Symptoms:**
- Service logs show command received
- Device doesn't lock
- No error messages

**Possible Causes and Solutions:**

a) **Device Admin Not Active**
   - Check if the app has Device Admin permission
   - Solution: Verify with `devicePolicyManager.isAdminActive(adminComponent)`
   - Request admin permission if not active:
     ```java
     Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
     intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
     startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
     ```

b) **Lock Implementation Issues**
   - The `lockDevice()` method might have issues
   - Solution: Ensure proper implementation:
     ```java
     if (devicePolicyManager.isAdminActive(adminComponent)) {
         devicePolicyManager.lockNow();
     }
     ```

c) **Android Version Compatibility**
   - Different Android versions handle device admin differently
   - Solution: Add version-specific code:
     ```java
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
         // Use newer APIs
     } else {
         // Use older APIs
     }
     ```

### 4. Testing and Verification

To verify the Remote Lock functionality:

1. **Use the Firebase Test Activity**
   - Run the `FirebaseTestActivity` to test each component separately
   - Check authentication, device registration, and lock commands

2. **Monitor Firebase Database**
   - Use the Firebase Console to monitor database changes
   - Verify that commands are being written to the correct paths

3. **Check Logs**
   - Monitor logcat for relevant log messages:
     ```
     adb logcat -s RemoteLockService:D RemoteLockManager:D DeviceRegistrationMgr:D
     ```

4. **Test with Two Devices**
   - Log in with the same account on two devices
   - Try sending lock commands between them

## Advanced Troubleshooting

### Database Structure Verification

Use this code to verify your database structure:

```java
DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
    @Override
    public void onDataChange(@NonNull DataSnapshot snapshot) {
        Log.d("Firebase", "Database structure: " + snapshot.toString());
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
        Log.e("Firebase", "Error reading database", error.toException());
    }
});
```

### Reset Database State

If you suspect corrupted data, reset the command state:

```java
String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
String deviceId = Settings.Secure.getString(
    getContentResolver(), Settings.Secure.ANDROID_ID);

DatabaseReference commandRef = FirebaseDatabase.getInstance().getReference()
    .child("commands").child(userId).child("devices").child(deviceId);

Map<String, Object> updates = new HashMap<>();
updates.put("lock", false);
updates.put("message", null);
updates.put("sender", null);

commandRef.updateChildren(updates);
```

## Need More Help?

If you're still experiencing issues:

1. Check the full implementation details in:
   - `RemoteLockManager.java`
   - `RemoteLockService.java`
   - `RemoteLockDeviceAdmin.java`
   - `DeviceRegistrationManager.java`

2. Verify your Firebase setup against the `firebase_setup_guide.md` document

3. Run the `FirebaseTestActivity` to diagnose specific components 