#!/bin/bash

# Setup Tor assets for Android
# This script downloads and sets up Tor binaries and GeoIP files

APP_ASSETS_DIR="app/src/main/assets"
TEMP_DIR="temp_tor_download"

echo "Setting up Tor assets for Android..."

# Create directories
mkdir -p "$APP_ASSETS_DIR"
mkdir -p "$TEMP_DIR"

# Download Tor binaries from Guardian Project
echo "Downloading Tor binaries..."

# For each architecture
declare -a ARCHS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

for arch in "${ARCHS[@]}"; do
    echo "Processing $arch..."
    
    # Create architecture directory
    mkdir -p "$APP_ASSETS_DIR/$arch"
    
    # Download Tor binary for this architecture
    # Note: These URLs are examples - you'll need to use actual Guardian Project releases
    case $arch in
        "arm64-v8a")
            BINARY_URL="https://github.com/guardianproject/tor-android/releases/download/0.4.7.13/tor-android-binary-0.4.7.13-aarch64.tar.xz"
            ;;
        "armeabi-v7a")
            BINARY_URL="https://github.com/guardianproject/tor-android/releases/download/0.4.7.13/tor-android-binary-0.4.7.13-armv7.tar.xz"
            ;;
        "x86")
            BINARY_URL="https://github.com/guardianproject/tor-android/releases/download/0.4.7.13/tor-android-binary-0.4.7.13-x86.tar.xz"
            ;;
        "x86_64")
            BINARY_URL="https://github.com/guardianproject/tor-android/releases/download/0.4.7.13/tor-android-binary-0.4.7.13-x86_64.tar.xz"
            ;;
    esac
    
    # Download and extract (if URL exists)
    if command -v wget &> /dev/null; then
        wget -O "$TEMP_DIR/tor-$arch.tar.xz" "$BINARY_URL" 2>/dev/null || echo "Could not download $arch binary"
    elif command -v curl &> /dev/null; then
        curl -L -o "$TEMP_DIR/tor-$arch.tar.xz" "$BINARY_URL" 2>/dev/null || echo "Could not download $arch binary"
    fi
    
    # Extract if download was successful
    if [ -f "$TEMP_DIR/tor-$arch.tar.xz" ]; then
        cd "$TEMP_DIR"
        tar -xf "tor-$arch.tar.xz"
        
        # Find and copy the tor binary
        find . -name "tor" -type f -executable | head -1 | xargs -I {} cp {} "../$APP_ASSETS_DIR/$arch/tor"
        
        cd ..
        echo "Extracted Tor binary for $arch"
    else
        echo "Warning: Could not download Tor binary for $arch"
        echo "You'll need to manually add the Tor binary to $APP_ASSETS_DIR/$arch/tor"
    fi
done

# Create a universal tor binary (use arm64 as default)
cp "$APP_ASSETS_DIR/arm64-v8a/tor" "$APP_ASSETS_DIR/tor" 2>/dev/null || echo "Creating universal binary failed"

# Download GeoIP files
echo "Downloading GeoIP files..."

GEOIP_URL="https://github.com/torproject/tor/raw/main/src/config/geoip"
GEOIP6_URL="https://github.com/torproject/tor/raw/main/src/config/geoip6"

if command -v wget &> /dev/null; then
    wget -O "$APP_ASSETS_DIR/geoip" "$GEOIP_URL" 2>/dev/null || echo "Could not download geoip file"
    wget -O "$APP_ASSETS_DIR/geoip6" "$GEOIP6_URL" 2>/dev/null || echo "Could not download geoip6 file"
elif command -v curl &> /dev/null; then
    curl -L -o "$APP_ASSETS_DIR/geoip" "$GEOIP_URL" 2>/dev/null || echo "Could not download geoip file"
    curl -L -o "$APP_ASSETS_DIR/geoip6" "$GEOIP6_URL" 2>/dev/null || echo "Could not download geoip6 file"
fi

# Create fallback files if download failed
if [ ! -f "$APP_ASSETS_DIR/geoip" ]; then
    echo "# Fallback GeoIP file" > "$APP_ASSETS_DIR/geoip"
    echo "Creating fallback geoip file"
fi

if [ ! -f "$APP_ASSETS_DIR/geoip6" ]; then
    echo "# Fallback GeoIP6 file" > "$APP_ASSETS_DIR/geoip6"
    echo "Creating fallback geoip6 file"
fi

# Clean up
rm -rf "$TEMP_DIR"

echo "Tor assets setup complete!"
echo ""
echo "Assets created in: $APP_ASSETS_DIR"
echo "- tor binaries for each architecture"
echo "- geoip and geoip6 files"
echo ""
echo "Note: If downloads failed, you'll need to manually add:"
echo "1. Tor binaries from https://github.com/guardianproject/tor-android/releases"
echo "2. GeoIP files from https://github.com/torproject/tor/tree/main/src/config"
echo ""
echo "Make sure to set executable permissions on Tor binaries!"