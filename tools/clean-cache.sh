#!/bin/sh
# Чистить кеш програм на телевізорі: sh tools/clean-cache.sh 192.168.1.50
IP="$1"; [ -z "$IP" ] && { echo "Вкажіть IP телевізора."; exit 1; }
ADB="$(command -v adb || echo "$(dirname "$0")/../platform-tools/adb")"
"$ADB" connect "$IP:5555" >/dev/null 2>&1
"$ADB" -s "$IP:5555" shell pm trim-caches 3G && echo "Кеш очищено."
