# Debugging Guide for PeerLinkyz2 Android Chat Application

## Overview
This guide helps debug connection and key exchange issues when testing the Android chat application on physical devices.

## Prerequisites
- Two Android devices with the app installed
- USB debugging enabled on both devices
- ADB (Android Debug Bridge) installed on your computer

## Enabling Verbose Logging

### Method 1: Using ADB
Connect your Android device via USB and run:
```bash
adb logcat | grep -E "(ChatActivity|WorkingTorService|P2pManager|P2pClient)"
```

### Method 2: Using Android Studio
1. Open Android Studio
2. Connect your device via USB
3. Go to View → Tool Windows → Logcat
4. Filter by tags: `ChatActivity`, `WorkingTorService`, `P2pManager`, `P2pClient`

## Expected Log Messages for Successful Operation

### 1. Tor Bootstrap Sequence
```
WorkingTorService: Starting working Tor service...
WorkingTorService: Bootstrap: 5% - Connecting to Tor network...
WorkingTorService: Bootstrap: 25% - Downloading directory info...
WorkingTorService: Bootstrap: 50% - Building circuits...
WorkingTorService: Bootstrap: 75% - Establishing connections...
WorkingTorService: Bootstrap: 100% - Tor Ready
```

### 2. Onion Address Generation/Restoration
```
WorkingTorService: Restored existing onion address: [56-char-address].onion
```
OR
```
WorkingTorService: Generated new persistent onion address: [56-char-address].onion
```

### 3. Successful Connection Establishment
```
ChatActivity: Starting P2pClient connection to: ws://[onion-address]:8080/chat
ChatActivity: Tor status: Tor Ready, Bootstrap: 100%
ChatActivity: Local peer address: ws://[onion-address]:8080/chat
P2pClient: WebSocket connected successfully
```

### 4. Successful Key Exchange
```
ChatActivity: Initiating handshake...
ChatActivity: Local Public Key Encoded: [base64-encoded-key]
ChatActivity: Received ECDH_PUBLIC_KEY message.
ChatActivity: Remote Public Key Decoded: [base64-encoded-key]
ChatActivity: Deriving shared secret...
ChatActivity: Shared Secret Derived. Key exchange complete: true
ChatActivity: Key exchange state saved for friend [friendId]
```

### 5. Successful Key Exchange Restoration
```
ChatActivity: Key exchange state restored for friend [friendId]
```

## Common Error Patterns and Solutions

### 1. App Crashes on Chat Initiation

**Error Pattern:**
```
FATAL EXCEPTION: main
Process: com.zsolutions.peerlinkyz, PID: [pid]
java.lang.IllegalArgumentException: Parameter specified as non-null is null
```

**Cause:** Null pointer exception when `localPeerId` is null
**Solution:** Fixed in latest version with proper null checks

### 2. "Key Exchange Not Complete" Error

**Error Pattern:**
```
ChatActivity: Key exchange not complete. Message not processed. isKeyExchangeComplete: false, sharedSecret: false
```

**Cause:** Key exchange timing issues or Tor not ready
**Solution:** 
- Wait for Tor to be fully ready (100% bootstrap)
- Check that both devices have valid onion addresses
- Verify key exchange state persistence

### 3. Tor Not Ready Issues

**Error Pattern:**
```
ChatActivity: Cannot send message: localPeerId is null (Tor may not be ready)
ChatActivity: Waiting for Tor to be ready...
ChatActivity: Tor still not ready after waiting
```

**Solution:**
- Wait longer for Tor bootstrap to complete
- Check network connectivity
- Restart the app if Tor fails to start

### 4. Connection Issues

**Error Pattern:**
```
P2pClient: WebSocket connection failed: [error message]
ChatActivity: P2pClient not connected, attempting to reconnect...
```

**Solution:**
- Verify both devices are on networks that allow Tor traffic
- Check that onion addresses are correctly formatted
- Ensure hidden service is running on the target device

## Debugging Steps

### Step 1: Verify Tor Bootstrap
1. Launch the app on both devices
2. Check logs for Tor bootstrap completion (100%)
3. Verify onion addresses are generated/restored

### Step 2: Test Connection
1. Add each device as a friend using their onion addresses
2. Initiate chat from Device A to Device B
3. Monitor logs for connection establishment

### Step 3: Test Key Exchange
1. Look for handshake initiation logs
2. Verify public key exchange messages
3. Check for shared secret derivation
4. Confirm key exchange state is saved

### Step 4: Test Message Sending
1. Send a test message from Device A
2. Verify message encryption and transmission
3. Check message reception and decryption on Device B

## Troubleshooting Checklist

- [ ] Both devices have Tor bootstrap at 100%
- [ ] Both devices have valid onion addresses
- [ ] Network allows Tor traffic on both devices
- [ ] Friend addresses are correctly entered
- [ ] Key exchange completes successfully
- [ ] Key exchange state persists across app restarts
- [ ] Messages can be sent and received

## Advanced Debugging

### Capture Full Logs
```bash
adb logcat > device_logs.txt
```

### Check App Storage
```bash
adb shell run-as com.zsolutions.peerlinkyz ls -la files/
adb shell run-as com.zsolutions.peerlinkyz ls -la files/tor_data/hidden_service/
```

### Monitor Network Traffic
Use tools like Wireshark to verify all traffic goes through Tor SOCKS proxy on port 9050.

## Getting Help

If issues persist after following this guide:
1. Capture full logs from both devices
2. Note the exact steps that lead to the issue
3. Include device models and Android versions
4. Report the issue with all collected information
