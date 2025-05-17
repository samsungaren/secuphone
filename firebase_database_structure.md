# Firebase Realtime Database Structure for SecuPhone

This document outlines the Firebase Realtime Database structure used in the SecuPhone app, with a focus on the remote lock functionality.

## Overall Structure

The database is organized into the following main paths:

```
/users/{uid}/devices/{device_id}  - Device registration information
/commands/{uid}/devices/{device_id} - Commands for specific devices
/commands/{uid}/lock - General lock command for all devices
/commands/{uid}/last_lock_target - Target device ID for the last lock command
/lock_status/{uid}/{device_id} - Status of lock commands for specific devices
```

## Detailed Structure

### Device Registration

```
/users/{uid}/devices/{device_id}/
    - model: "Pixel 6"              // Device model
    - manufacturer: "Google"        // Device manufacturer
    - name: "pixel"                 // Device name
    - last_seen: 1627984561234      // Timestamp of last activity
    - online: true                  // Online status
```

### Device Commands

```
/commands/{uid}/devices/{device_id}/
    - lock: true/false              // Lock command flag
    - message: "Device locked"      // Optional lock message
    - sender: "{sender_device_id}"  // ID of the device that sent the command
```

### General Commands

```
/commands/{uid}/
    - lock: true/false              // General lock command for all devices
    - message: "All devices locked" // Optional message for all devices
    - sender: "{sender_device_id}"  // ID of the device that sent the command
    - last_lock_target: "{device_id}" // ID of the last device targeted for locking
```

### Lock Status

```
/lock_status/{uid}/{device_id}/
    - command_sent: true/false      // Whether a lock command was sent
    - locked: true/false            // Whether the device was successfully locked
```

## How It Works

1. **Device Registration**:
   - When a device is registered, it creates an entry in `/users/{uid}/devices/{device_id}`
   - The device periodically updates its online status and last_seen timestamp

2. **Sending Lock Commands**:
   - To lock a specific device, the app writes to `/commands/{uid}/devices/{device_id}/lock` with value `true`
   - It also sets the sender ID and optional message
   - Additionally, it updates `/commands/{uid}/last_lock_target` with the target device ID

3. **Receiving Lock Commands**:
   - The `RemoteLockService` listens for changes to the following paths:
     - `/commands/{uid}/lock` (general lock command)
     - `/commands/{uid}/devices/{device_id}/lock` (device-specific command)
     - `/commands/{uid}/last_lock_target` (target device indicator)
   - When a command is detected, it checks if the sender is different from the current device
   - If conditions are met, it locks the device using the DevicePolicyManager

4. **Status Updates**:
   - After processing a lock command, the service updates `/lock_status/{uid}/{device_id}/locked` to `true`
   - When sending a command, the app updates `/lock_status/{uid}/{device_id}/command_sent` to `true`

## Security Rules

Recommended security rules for this structure:

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

These rules ensure that users can only read and write data related to their own user ID, providing proper isolation between different users' devices and commands. 