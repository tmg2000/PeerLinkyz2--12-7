# Tor Integration Guide

This document explains how to integrate embedded Tor into your Android application.

## Current Implementation Status

The current implementation provides a framework for embedded Tor but uses mock/simulated functionality. To get actual Tor working, you need to add real Tor binaries.

## What's Implemented

- ✅ Tor service framework (`TorService.kt`)
- ✅ Configuration file generation
- ✅ Hidden service setup
- ✅ SOCKS proxy configuration
- ✅ WebSocket integration with Tor
- ✅ Network security configuration for .onion domains
- ✅ Error handling and retry logic
- ✅ Connection status monitoring

## What's Missing (For Production)

### 1. Tor Binaries
You need to include actual Tor binaries for different Android architectures:
- `arm64-v8a` (64-bit ARM)
- `armeabi-v7a` (32-bit ARM)
- `x86` (32-bit Intel)
- `x86_64` (64-bit Intel)

### 2. How to Add Tor Binaries

1. **Get Tor binaries for Android:**
   ```bash
   # Option 1: Build from source
   git clone https://github.com/torproject/tor
   # Follow Android cross-compilation instructions
   
   # Option 2: Use Guardian Project's builds
   # Download from: https://github.com/guardianproject/tor-android
   ```

2. **Add to your project:**
   ```
   app/src/main/assets/
   ├── tor/
   │   ├── arm64-v8a/
   │   │   └── tor
   │   ├── armeabi-v7a/
   │   │   └── tor
   │   ├── x86/
   │   │   └── tor
   │   └── x86_64/
   │       └── tor
   ```

3. **Update `TorService.kt`:**
   Replace the `extractTorBinary()` method with:
   ```kotlin
   private fun extractTorBinary() {
       val abi = Build.SUPPORTED_ABIS[0]
       val assetPath = "tor/$abi/tor"
       
       try {
           context.assets.open(assetPath).use { input ->
               FileOutputStream(torBinary).use { output ->
                   input.copyTo(output)
               }
           }
           
           // Set executable permissions
           torBinary.setExecutable(true)
           
       } catch (e: IOException) {
           throw RuntimeException("Failed to extract Tor binary for $abi", e)
       }
   }
   ```

4. **Update `startTorProcess()`:**
   ```kotlin
   private fun startTorProcess() {
       val processBuilder = ProcessBuilder(
           torBinary.absolutePath,
           "-f", torConfigFile.absolutePath
       )
       
       processBuilder.directory(torDataDir)
       processBuilder.redirectErrorStream(true)
       
       torProcess = processBuilder.start()
       
       // Monitor process output
       scope.launch {
           torProcess?.inputStream?.bufferedReader()?.useLines { lines ->
               lines.forEach { line ->
                   Log.d("TorService", "Tor: $line")
                   if (line.contains("Bootstrapped 100%")) {
                       _status.value = "Ready"
                   }
               }
           }
       }
   }
   ```

## Alternative: Using Guardian Project's Netcipher

If you prefer not to manage Tor binaries yourself, you can use NetCipher with Orbot:

```kotlin
// Add to build.gradle.kts
implementation("info.guardianproject.netcipher:netcipher:2.1.0")
implementation("info.guardianproject.netcipher:netcipher-okhttp3:2.1.0")

// Usage
val proxy = NetCipherProxyHelper.getProxy()
val client = HttpClient(OkHttp) {
    engine {
        proxy = proxy
    }
}
```

## Testing

1. **Mock Mode (Current):**
   - Uses simulated Tor functionality
   - Good for UI testing and development
   - Generates mock .onion addresses

2. **Real Tor Mode:**
   - Requires actual Tor binaries
   - Full Tor network connectivity
   - Real .onion addresses

## Security Considerations

1. **Permissions:**
   - Ensure Tor binary has executable permissions
   - Limit file access to app directory

2. **Network Security:**
   - The app includes network security config for .onion domains
   - Cleartext traffic is allowed for Tor connections

3. **Data Protection:**
   - Tor data directory is in app private storage
   - Hidden service keys are protected

## Troubleshooting

1. **Tor fails to start:**
   - Check logs in `tor_data/notices.log`
   - Verify binary permissions
   - Ensure correct architecture

2. **Connection issues:**
   - Check if SOCKS proxy is running on port 9050
   - Verify network security configuration
   - Test with direct IP connections first

3. **Hidden service issues:**
   - Check `hidden_service/hostname` file exists
   - Verify port forwarding configuration
   - Monitor Tor logs for service creation

## Files Modified

- `TorService.kt` - Main Tor service implementation
- `P2pManager.kt` - Integration with P2P messaging
- `PeerLinkyzApplication.kt` - HttpClient configuration
- `network_security_config.xml` - Network security settings
- `AndroidManifest.xml` - Permissions and security config

## Performance Notes

- Tor startup can take 10-30 seconds
- Hidden service creation adds additional time
- SOCKS proxy adds latency to connections
- Consider connection pooling for better performance