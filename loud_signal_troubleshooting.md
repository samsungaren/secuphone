# Loud Signal Feature Troubleshooting Guide

## Common Firebase Database Errors

When using the Loud Signal feature, you might encounter Firebase database errors. Here are the most common issues and their solutions:

### 1. Authentication Issues

**Error:** `Firebase Database error: Permission denied`

**Solution:**
- Make sure you're signed in to the app
- Check that your Firebase Authentication is properly set up
- Verify the Firebase rules allow read/write access to the signals path

```json
// Example Firebase rules to fix permission issues
{
  "rules": {
    "signals": {
      "$deviceId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

### 2. Database Structure Issues

**Error:** `Firebase Database error: Invalid path`

**Solution:**
- Ensure the Firebase database exists at the correct URL
- Check that the database structure matches what the app expects:

```
firebase-root/
  └── signals/
      └── [device-id]/
          ├── type: "start" or "stop"
          ├── durationSeconds: integer
          ├── volumeLevel: integer
          └── timestamp: long
```

### 3. Network Connectivity Issues

**Error:** `Firebase Database error: Network error`

**Solution:**
- Check your internet connection
- Verify that Firebase services are not down
- Try again when you have a stable connection

### 4. Missing Firebase Configuration

**Error:** `Firebase Database error: Failed to initialize`

**Solution:**
- Ensure the `google-services.json` file is properly added to your project
- Verify that all required Firebase dependencies are in your app's build.gradle file:

```gradle
// Required Firebase dependencies
implementation 'com.google.firebase:firebase-database:20.0.0'
implementation 'com.google.firebase:firebase-auth:21.0.1'
```

### 5. Incorrect Device ID

**Error:** `Firebase Database error: Device not found`

**Solution:**
- Verify that the device ID being used is correct
- Check that the device is registered in the Firebase database
- Make sure the paired device exists and is online

## Debugging Steps

1. **Enable Detailed Logging:**
   Add this code to your application class to see detailed Firebase logs:
   ```java
   FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
   ```

2. **Check Firebase Console:**
   - Go to the Firebase Console
   - Navigate to Realtime Database
   - Check if data is being written to the correct path
   - Verify permissions and rules

3. **Test with Direct Database Write:**
   Use the Firebase console to manually write a signal command to test if the receiving device responds correctly.

4. **Verify Database URL:**
   Make sure your app is connecting to the correct Firebase database URL:
   ```java
   FirebaseDatabase database = FirebaseDatabase.getInstance("https://your-project-id.firebaseio.com/");
   ```

## Specific Error Solutions

### Error: "User does not have permission to access this object"

This typically means your Firebase rules are too restrictive. Update your rules to allow authenticated users to access the signals path.

### Error: "Cannot convert object to type Integer"

This happens when the database structure doesn't match what the app expects. Make sure your signal data has the correct types:
- `durationSeconds` should be an integer
- `volumeLevel` should be an integer
- `type` should be a string ("start" or "stop")

### Error: "Failed to parse Firebase url"

Check that your Firebase project is properly set up and the URL is correct in your initialization code.

## Contact Support

If you continue experiencing issues after trying these solutions, please contact our support team with:
1. The exact error message
2. Steps to reproduce the issue
3. Your device model and Android version
4. Screenshots of the error if available 