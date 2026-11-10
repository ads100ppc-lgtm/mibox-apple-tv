#!/bin/sh
# Налаштування телевізора/приставки на Android TV: лаунчер у стилі Apple TV + кнопки пульта.
# Запуск з комп'ютера (Mac або Linux), телевізор і комп'ютер — в одній мережі Wi-Fi:
#   sh setup.sh 192.168.1.50
#   sh setup.sh 192.168.1.50 --clean            (ще й прибрати зайві програми Xiaomi)
#   sh setup.sh 192.168.1.50 --city Київ --lat 50.4501 --lon 30.5234
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
IP=""; CLEAN=0; CITY=""; LAT=""; LON=""
while [ $# -gt 0 ]; do
  case "$1" in
    --clean) CLEAN=1 ;;
    --city) CITY="$2"; shift ;;
    --lat) LAT="$2"; shift ;;
    --lon) LON="$2"; shift ;;
    *) IP="$1" ;;
  esac
  shift
done

say() { printf '\n== %s\n' "$*"; }

# 1. adb
if command -v adb >/dev/null 2>&1; then ADB=adb
elif [ -x "$DIR/platform-tools/adb" ]; then ADB="$DIR/platform-tools/adb"
else
  say "Завантажую adb (офіційний набір platform-tools від Google)"
  case "$(uname -s)" in Darwin) OS=darwin ;; Linux) OS=linux ;; *) echo "Підтримуються Mac і Linux."; exit 1 ;; esac
  curl -fL "https://dl.google.com/android/repository/platform-tools-latest-$OS.zip" -o "$DIR/pt.zip"
  unzip -q -o "$DIR/pt.zip" -d "$DIR" && rm "$DIR/pt.zip"
  ADB="$DIR/platform-tools/adb"
fi

# 2. підключення
if [ -z "$IP" ]; then
  printf "IP-адреса телевізора (Налаштування → Мережа; на ТВ має бути увімкнене «Налагодження USB»): "
  read IP
fi
say "Підключаюсь до $IP"
"$ADB" connect "$IP:5555" >/dev/null 2>&1 || true
n=0
while [ "$("$ADB" -s "$IP:5555" get-state 2>/dev/null)" != "device" ]; do
  n=$((n+1)); [ $n -gt 30 ] && { echo "Не вдалося підключитись. Перевірте адресу й дозвіл налагодження на екрані ТВ."; exit 1; }
  [ $n -eq 1 ] && echo "Якщо на екрані ТВ з'явиться запит — виберіть «Завжди дозволяти» і «ОК»."
  sleep 2; "$ADB" connect "$IP:5555" >/dev/null 2>&1 || true
done
D() { "$ADB" -s "$IP:5555" shell "$@"; }
MODEL="$(D getprop ro.product.model | tr -d '\r')"; echo "Пристрій: $MODEL"

# 3. встановлення
say "Встановлюю лаунчер і програму для кнопок"
"$ADB" -s "$IP:5555" install -r "$DIR/apks/home.apk" >/dev/null
"$ADB" -s "$IP:5555" install -r "$DIR/apks/keylauncher.apk" >/dev/null
for p in READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  D pm grant ua.pc.home android.permission.$p >/dev/null 2>&1 || true
done

# 4. перший запуск: лаунчер сам створює свою папку (так потрібно для прав доступу)
say "Перший запуск і фонові картинки"
D am start -n ua.pc.home/.HomeActivity >/dev/null
n=0
until D ls /sdcard/PcHome/wallpapers >/dev/null 2>&1; do
  n=$((n+1)); [ $n -gt 30 ] && break; sleep 1
done
D 'rm -f /sdcard/PcHome/wallpapers/gen_*.jpg'
"$ADB" -s "$IP:5555" push "$DIR/assets/wallpapers/." /sdcard/PcHome/wallpapers/ >/dev/null
D 'rm -f /sdcard/PcHome/wallpapers/CREDITS.txt' >/dev/null 2>&1 || true
"$ADB" -s "$IP:5555" push "$DIR/assets/wallpapers/CREDITS.txt" /sdcard/PcHome/CREDITS.txt >/dev/null

# 5. налаштування лаунчера (наявний файл не перезаписуємо)
if ! D 'test -f /sdcard/PcHome/config.json' 2>/dev/null; then
  "$ADB" -s "$IP:5555" push "$DIR/config.example.json" /sdcard/PcHome/config.json >/dev/null
fi
if [ -n "$CITY$LAT$LON" ]; then
  TMP="$(mktemp)"; "$ADB" -s "$IP:5555" pull /sdcard/PcHome/config.json "$TMP" >/dev/null
  python3 - "$TMP" "$CITY" "$LAT" "$LON" <<'PY'
import json, sys
p, city, lat, lon = sys.argv[1:5]
c = json.load(open(p))
if city: c["city"] = city
if lat and lon: c["lat"] = float(lat); c["lon"] = float(lon)
json.dump(c, open(p, "w"), ensure_ascii=False, indent=2)
PY
  "$ADB" -s "$IP:5555" push "$TMP" /sdcard/PcHome/config.json >/dev/null; rm -f "$TMP"
fi

# 6. кнопки пульта (служба доступності)
say "Вмикаю програму для кнопок пульта"
SVC="ua.pc.keymap/ua.pc.keymap.KeyService"
CUR="$(D settings get secure enabled_accessibility_services | tr -d '\r')"
case "$CUR" in
  *ua.pc.keymap*) ;;
  ""|null) D settings put secure enabled_accessibility_services "$SVC" ;;
  *) D settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
esac
D settings put secure accessibility_enabled 1

# 7. системні налаштування
say "Системні налаштування"
D settings put global hdmi_control_auto_language_change_enabled 0   # телевізор не міняє мову приставки
D settings put global window_animation_scale 0.7
D settings put global transition_animation_scale 0.7
D settings put global animator_duration_scale 0.7
D settings put system screen_off_timeout 900000                     # системна заставка не перебиває власну
D settings put secure location_mode 3                               # потрібне, щоб бачити список мереж Wi-Fi

# 8. зробити лаунчер головним екраном
say "Роблю лаунчер головним екраном"
D cmd package set-home-activity ua.pc.home/.HomeActivity

# 9. прибирання зайвого (лише з --clean і лише на Xiaomi)
if [ $CLEAN -eq 1 ]; then
  case "$(D getprop ro.product.manufacturer | tr -d '\r')" in
    Xiaomi|xiaomi)
      say "Вимикаю зайві програми Xiaomi (повернути: cmd package install-existing --user 0 <пакет>)"
      while read -r P; do
        [ -n "$P" ] && D pm uninstall -k --user 0 "$P" >/dev/null 2>&1 && echo "  вимкнено $P" || true
      done < "$DIR/tools/bloat-xiaomi.txt" ;;
    *) echo "Це не Xiaomi — список зайвого не застосовую." ;;
  esac
fi

# 10. які потрібні програми ще не встановлено
say "Перевірка програм для плиток"
for P in com.google.android.youtube.tv:YouTube com.netflix.ninja:Netflix top.rootu.lampa:Lampa com.kyivstar.tv.androidtv:"Київстар ТБ" com.megogo.application:Megogo com.macpaw.clearvpn.android:ClearVPN; do
  PKG="${P%%:*}"; NAME="${P#*:}"
  D pm path "$PKG" >/dev/null 2>&1 || echo "  немає: $NAME — встановіть з Google Play, плитка з'явиться сама"
done

D input keyevent KEYCODE_HOME
say "Готово. Натисніть на пульті кнопку Home."
echo "Кнопки YouTube, Netflix, Apps, Xiaomi TV+ і Prime Video працюють через програму «Key Launcher»."
echo "Якщо пульт інший і кнопка не реагує — див. розділ про коди кнопок у README."
