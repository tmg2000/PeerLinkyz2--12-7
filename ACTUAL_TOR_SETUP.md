# Actual Tor Setup Instructions

This guide will help you set up real Tor functionality in your Android application.

## Current Status

✅ **Completed:**
- Real Tor service implementation (`RealTorService.kt`)
- Tor binary extraction and configuration
- Bootstrap progress monitoring
- Hidden service setup
- Control port communication
- Network security configuration
- Integration with P2P messaging

⚠️ **Requires Manual Setup:**
- Actual Tor binaries (placeholder files created)
- GeoIP files (placeholder files created)

## Method 1: Automatic Download (Recommended)

Run the Python script to automatically download Tor binaries:

```bash
cd "/home/tmg/PeerLinkz/PeerLinkyz2  12-7"
python3 download_tor_binaries.py
```

This will:
- Download Tor binaries for all Android architectures
- Download GeoIP files
- Set up the assets directory structure

## Method 2: Manual Download

If the automatic script doesn't work, manually download:

### 1. Tor Binaries

Download from Guardian Project releases:
- **URL:** https://github.com/guardianproject/tor-android/releases/latest
- **Files needed:**
  - `tor-android-0.4.7.13-aarch64.tar.gz` (for arm64-v8a)
  - `tor-android-0.4.7.13-armv7.tar.gz` (for armeabi-v7a)
  - `tor-android-0.4.7.13-i686.tar.gz` (for x86)
  - `tor-android-0.4.7.13-x86_64.tar.gz` (for x86_64)

### 2. Extract and Place Binaries

```bash
# Create directories
mkdir -p app/src/main/assets/arm64-v8a
mkdir -p app/src/main/assets/armeabi-v7a
mkdir -p app/src/main/assets/x86
mkdir -p app/src/main/assets/x86_64

# Extract binaries (example for arm64)
tar -xzf tor-android-0.4.7.13-aarch64.tar.gz
cp path/to/extracted/tor app/src/main/assets/arm64-v8a/tor

# Repeat for other architectures
```

### 3. GeoIP Files

Download from Tor project:
```bash
wget -O app/src/main/assets/geoip https://raw.githubusercontent.com/torproject/tor/main/src/config/geoip
wget -O app/src/main/assets/geoip6 https://raw.githubusercontent.com/torproject/tor/main/src/config/geoip6
```

## Method 3: Using Tor Expert Bundle

Alternative approach using Tor Expert Bundle:

1. Download Tor Expert Bundle for Android from torproject.org
2. Extract the `tor` binary
3. Place in assets directory

## Verification

After setting up the binaries, verify the setup:

1. **Check file structure:**
   ```
   app/src/main/assets/
   ├── arm64-v8a/tor
   ├── armeabi-v7a/tor
   ├── x86/tor
   ├── x86_64/tor
   ├── tor (universal binary)
   ├── geoip
   └── geoip6
   ```

2. **Test the application:**
   - Install and run the app
   - Check logs for Tor bootstrap progress
   - Verify hidden service creation
   - Test messaging functionality

## Expected Behavior

When working correctly, you should see:

1. **Tor Bootstrap Logs:**
   ```
   D/RealTorService: Starting Tor service...
   D/RealTorService: Tor: Bootstrapped 0%
   D/RealTorService: Tor: Bootstrapped 25%
   D/RealTorService: Tor: Bootstrapped 50%
   D/RealTorService: Tor: Bootstrapped 75%
   D/RealTorService: Tor: Bootstrapped 100%
   D/RealTorService: Hidden service address: abcd1234.onion
   ```

2. **Network Connectivity:**
   - SOCKS proxy on 127.0.0.1:9050
   - Control port on 127.0.0.1:9051
   - Hidden service accessible via .onion address

## Troubleshooting

### Common Issues:

1. **"Tor binary not found"**
   - Ensure binary is in correct assets directory
   - Check file permissions
   - Verify architecture compatibility

2. **"Bootstrap failed"**
   - Check internet connectivity
   - Verify GeoIP files are present
   - Check Tor logs in app data directory

3. **"Permission denied"**
   - Android doesn't allow executing binaries from assets
   - Binary must be copied to app's files directory
   - Set executable permissions

### Debug Steps:

1. **Check logs:**
   ```bash
   adb logcat | grep -E "(RealTorService|P2pManager|Tor)"
   ```

2. **Verify network connectivity:**
   ```bash
   # Test SOCKS proxy
   curl --socks5 127.0.0.1:9050 https://check.torproject.org/
   ```

3. **Check hidden service:**
   ```bash
   # Look for hostname file
   adb shell cat /data/data/com.zsolutions.peerlinkyz/files/tor/hidden_service/hostname
   ```

## Security Notes

- Tor data is stored in app's private directory
- Hidden service private keys are protected
- SOCKS proxy only accepts local connections
- Network security config allows .onion domains

## Performance Considerations

- Tor bootstrap takes 10-60 seconds
- Hidden service creation adds 10-30 seconds
- SOCKS proxy adds latency to connections
- Consider connection pooling for better performance

## Alternative: Using Orbot

If you prefer using Orbot instead of embedded Tor:

1. Remove `RealTorService` usage
2. Use `TorUtils` to check for Orbot
3. Configure app to use Orbot's SOCKS proxy
4. Handle Orbot installation/startup

## Files Modified for Real Tor

- `RealTorService.kt` - Main Tor service implementation
- `P2pManager.kt` - Updated to use RealTorService
- `build.gradle.kts` - Added Tor dependencies
- `assets/` - Tor binaries and GeoIP files
- `download_tor_binaries.py` - Automatic download script

## Next Steps

1. Set up Tor binaries using one of the methods above
2. Test the application
3. Monitor Tor bootstrap progress
4. Verify hidden service creation
5. Test P2P messaging over Tor

The implementation is ready for real Tor - you just need to provide the actual Tor binaries!