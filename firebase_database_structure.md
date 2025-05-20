# Firebase Database Structure for SecuPhone

This document outlines the structure of the Firebase Realtime Database used in the SecuPhone app, with a focus on the Loud Signal feature.

## Database Root Structure

```
secuphone-f2660-default-rtdb/
├── users/
│   └── [user-uid]/
│       ├── devices/
│       │   └── [device-id]/
│       │       ├── model: "string"
│       │       ├── manufacturer: "string"
│       │       ├── name: "string"
│       │       ├── last_seen: timestamp
│       │       └── online: boolean
│       └── settings/
│           └── ...
├── commands/
│   └── [user-uid]/
│       ├── devices/
│       │   └── [device-id]/
│       │       ├── lock: boolean
│       │       ├── message: "string"
│       │       └── sender: "string"
│       └── last_lock_target: "string"
├── signals/
│   └── [device-id]/
│       ├── type: "start" or "stop"
│       ├── durationSeconds: integer
│       ├── volumeLevel: integer
│       ├── timestamp: timestamp
│       └── senderUid: "string"
└── lock_status/
    └── [user-uid]/
        └── [device-id]/
            └── command_sent: boolean
```

## Loud Signal Feature Database Nodes

The Loud Signal feature primarily uses the `signals` node in the database:

### signals/[device-id]

This node contains the signal commands for a specific device:

- **type**: String - Either "start" to begin a signal or "stop" to end it
- **durationSeconds**: Integer - How long the signal should play (for "start" commands)
- **volumeLevel**: Integer - Volume level from 0-100 (for "start" commands)
- **timestamp**: Long - When the command was sent
- **senderUid**: String - The Firebase UID of the user who sent the command

## Data Flow for Loud Signal Feature

1. **Sending a Signal Command**:
   - When a user sends a signal command to a device, the app writes to `signals/[target-device-id]`
   - The data includes the type ("start"), duration, volume level, timestamp, and sender UID

2. **Receiving a Signal Command**:
   - Each device listens to `signals/[its-own-device-id]` for changes
   - When a command is received, the device checks the type:
     - If "start", it plays the loud signal with the specified duration and volume
     - If "stop", it stops any currently playing signal

3. **Stopping a Signal**:
   - To stop a signal, the app writes to the same path with type "stop"
   - The receiving device will immediately stop the signal when this command is received

## Security Rules

The following Firebase security rules should be applied to ensure proper access control:

```json
{
  "rules": {
    "signals": {
      "$deviceId": {
        ".read": "auth != null",
        ".write": "auth != null"
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
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

These rules ensure that:
- Only authenticated users can read or write signal commands
- Users can only read and write to their own user data
- Device information can be read by any authenticated user but only written by the device owner

## Debugging Tips

1. **Monitoring Signal Commands**:
   - Use the Firebase console to monitor the `signals/[device-id]` node
   - You can manually add a test signal command to verify device response

2. **Checking Device Registration**:
   - Verify that devices are properly registered under `users/[user-uid]/devices/`
   - Ensure the `last_seen` timestamp is recent and `online` status is correct

3. **Common Issues**:
   - Missing or incorrect device IDs
   - Authentication issues (user not signed in)
   - Network connectivity problems
   - Incorrect database path structure

## Implementation Notes

- The app uses Firebase Database listeners to react to signal commands in real-time
- Signal commands are not persisted long-term; they are processed immediately
- Each device should maintain only one active listener for its signal commands
- The database is configured for offline persistence to handle temporary network issues 