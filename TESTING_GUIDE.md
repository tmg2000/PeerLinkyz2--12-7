# Testing Guide for PeerLinkyz2 Android Chat Application

## Overview
This guide provides comprehensive testing instructions for the Android chat application with Tor-only networking, persistent onion addresses, and key exchange functionality.

## Prerequisites
- Two Android devices or emulators with KVM support
- Android Debug Bridge (adb) installed
- Built APK file: `app/build/outputs/apk/debug/app-debug.apk`

## Core Features to Test

### 1. Onion Address Persistence
**Objective**: Verify that the onion address remains constant across app restarts.

**Steps**:
1. Install and launch the app on Device A
2. Navigate to settings or peer discovery to view the onion address
3. Record the onion address (format: `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.onion`)
4. Force close the app completely
5. Restart the app
6. Verify the onion address is identical to the recorded address

**Expected Result**: The onion address should remain exactly the same after restart.

**Files Involved**:
- `WorkingTorService.kt` - implements `generateOrRestoreOnionAddress()`
- Hidden service files: `hostname`, `hs_ed25519_secret_key`, `hs_ed25519_public_key`

### 2. Key Exchange Completion and Persistence
**Objective**: Verify that key exchange completes successfully and persists across app restarts.

**Steps**:
1. Install the app on both Device A and Device B
2. On Device A, add Device B as a friend using Device B's onion address
3. On Device B, add Device A as a friend using Device A's onion address
4. Initiate a chat from Device A to Device B
5. Observe the key exchange process in logs or UI
6. Verify "Key exchange complete!" message appears
7. Send a test message from Device A to Device B
8. Verify the message is received and decrypted on Device B
9. Force close both apps
10. Restart both apps
11. Send another message between the devices
12. Verify no new key exchange is required and messages work immediately

**Expected Result**: 
- Initial key exchange should complete successfully
- Messages should be sent/received after key exchange
- After restart, no new key exchange should be needed
- Messages should continue working immediately after restart

**Files Involved**:
- `ChatActivity.kt` - implements key exchange state persistence
- SharedPreferences keys: `shared_secret_$friendId`, `local_private_key_$friendId`, etc.

### 3. Tor-Only Networking
**Objective**: Verify all network traffic goes through Tor.

**Steps**:
1. Monitor network traffic using tools like Wireshark or tcpdump
2. Launch the app and establish connections
3. Verify no direct HTTP/HTTPS connections are made
4. Verify all traffic goes through SOCKS proxy on port 9050
5. Check that fallback connections are not used

**Expected Result**: All network traffic should route through Tor SOCKS proxy.

**Files Involved**:
- `PeerLinkyzApplication.kt` - removed fallback HttpClient
- `P2pManager.kt` - initializes P2pClient with Tor-enabled HttpClient
- `P2pClient.kt` - uses injected HttpClient instead of creating its own

## Alternative Testing Methods (No Emulator Required)

### Method 1: Network Simulation Testing
Create a local test environment that simulates the Tor network behavior without requiring full emulation.

### Method 2: Unit Testing Key Components
Test individual components in isolation:
- Key exchange logic
- Onion address generation/restoration
- Message encryption/decryption

### Method 3: Log Analysis
Enable verbose logging and analyze the application behavior through log output:
```bash
adb logcat | grep -E "(ChatActivity|WorkingTorService|P2pManager|P2pClient)"
```

### Method 4: Manual Testing with Physical Devices
Use two physical Android devices connected via USB debugging for comprehensive testing.

## Troubleshooting

### "Key exchange not complete" Error
If this error persists:
1. Check that both devices can reach each other's onion addresses
2. Verify Tor service is running on both devices
3. Check that key exchange state is being saved properly
4. Clear app data and retry if state becomes corrupted

### Onion Address Changes
If onion address changes between restarts:
1. Check file permissions on hidden service directory
2. Verify key files are being created and preserved
3. Check for storage permission issues

### Connection Issues
If devices cannot connect:
1. Verify both devices are on networks that allow Tor traffic
2. Check that onion addresses are correctly formatted
3. Verify Tor bootstrap completed successfully

## Expected Log Messages

### Successful Onion Address Restoration
```
WorkingTorService: Restored existing onion address: [address].onion
```

### Successful Key Exchange
```
ChatActivity: Shared Secret Derived. Key exchange complete: true
ChatActivity: Key exchange state saved for friend [friendId]
```

### Successful Key Exchange Restoration
```
ChatActivity: Key exchange state restored for friend [friendId]
```

## Testing Checklist

- [ ] Onion address persists across app restarts
- [ ] Key exchange completes successfully between two devices
- [ ] Messages can be sent and received after key exchange
- [ ] Key exchange state persists across app restarts
- [ ] No "Key exchange not complete" errors after successful exchange
- [ ] All network traffic routes through Tor
- [ ] No fallback connections are used
- [ ] App builds successfully without errors
- [ ] Hidden service files are created and preserved
