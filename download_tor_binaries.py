#!/usr/bin/env python3
"""
Download Tor binaries for Android from Guardian Project
"""

import os
import requests
import tarfile
import zipfile
import shutil
from pathlib import Path

# Constants
GUARDIAN_PROJECT_BASE_URL = "https://github.com/guardianproject/tor-android/releases/download"
TOR_VERSION = "0.4.7.13"
ASSETS_DIR = Path("app/src/main/assets")

# Architecture mappings
ARCHITECTURES = {
    "arm64-v8a": "aarch64",
    "armeabi-v7a": "armv7",
    "x86": "i686", 
    "x86_64": "x86_64"
}

def download_file(url, dest_path):
    """Download a file from URL to destination path"""
    try:
        print(f"Downloading {url}...")
        response = requests.get(url, stream=True)
        response.raise_for_status()
        
        with open(dest_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"Downloaded to {dest_path}")
        return True
    except Exception as e:
        print(f"Failed to download {url}: {e}")
        return False

def extract_tor_binary(archive_path, output_dir):
    """Extract Tor binary from archive"""
    try:
        if archive_path.suffix == '.gz':
            with tarfile.open(archive_path, 'r:gz') as tar:
                for member in tar.getmembers():
                    if member.name.endswith('/tor') and member.isfile():
                        member.name = 'tor'
                        tar.extract(member, output_dir)
                        return True
        elif archive_path.suffix == '.zip':
            with zipfile.ZipFile(archive_path, 'r') as zip_ref:
                for file_info in zip_ref.infolist():
                    if file_info.filename.endswith('/tor') and not file_info.is_dir():
                        file_info.filename = 'tor'
                        zip_ref.extract(file_info, output_dir)
                        return True
        return False
    except Exception as e:
        print(f"Failed to extract {archive_path}: {e}")
        return False

def download_tor_assets():
    """Download Tor binaries and GeoIP files"""
    
    # Create assets directory
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    
    # Download Tor binaries for each architecture
    for android_arch, tor_arch in ARCHITECTURES.items():
        print(f"\nProcessing {android_arch} ({tor_arch})...")
        
        # Try different possible filenames
        possible_files = [
            f"tor-{TOR_VERSION}-{tor_arch}.tar.gz",
            f"tor-android-{TOR_VERSION}-{tor_arch}.tar.gz",
            f"tor-{tor_arch}.tar.gz"
        ]
        
        arch_dir = ASSETS_DIR / android_arch
        arch_dir.mkdir(exist_ok=True)
        
        downloaded = False
        for filename in possible_files:
            url = f"{GUARDIAN_PROJECT_BASE_URL}/{TOR_VERSION}/{filename}"
            temp_file = Path(f"temp_{filename}")
            
            if download_file(url, temp_file):
                if extract_tor_binary(temp_file, arch_dir):
                    print(f"Successfully extracted Tor binary for {android_arch}")
                    downloaded = True
                    temp_file.unlink()
                    break
                temp_file.unlink()
        
        if not downloaded:
            print(f"Warning: Could not download Tor binary for {android_arch}")
            print(f"Please manually download from: {GUARDIAN_PROJECT_BASE_URL}/{TOR_VERSION}/")
    
    # Create universal tor binary (copy from arm64 if available)
    universal_tor = ASSETS_DIR / "tor"
    arm64_tor = ASSETS_DIR / "arm64-v8a" / "tor"
    
    if arm64_tor.exists():
        shutil.copy2(arm64_tor, universal_tor)
        print("Created universal Tor binary")
    
    # Download GeoIP files
    print("\nDownloading GeoIP files...")
    
    geoip_files = [
        ("geoip", "https://raw.githubusercontent.com/torproject/tor/main/src/config/geoip"),
        ("geoip6", "https://raw.githubusercontent.com/torproject/tor/main/src/config/geoip6")
    ]
    
    for filename, url in geoip_files:
        dest_path = ASSETS_DIR / filename
        if download_file(url, dest_path):
            print(f"Downloaded {filename}")
        else:
            print(f"Warning: Could not download {filename}")
            # Create minimal fallback
            with open(dest_path, 'w') as f:
                f.write("# Fallback GeoIP file\n")
    
    print("\nTor assets download complete!")
    print(f"Assets saved to: {ASSETS_DIR}")
    print("\nNext steps:")
    print("1. Verify the Tor binaries are executable")
    print("2. Test the application")
    print("3. If binaries don't work, manually download from Guardian Project")

if __name__ == "__main__":
    download_tor_assets()