#!/usr/bin/env bash
# 在模擬器上實測：MP3 → 切到 MP4 → 回桌面（背景播放）→ 重新開啟
set -x
PKG=com.rcj.pocketplayer
OUT=smoke
mkdir -p $OUT
note() { echo "### $*" | tee -a $OUT/steps.txt; }
playing() { echo "播放狀態: $(adb shell dumpsys media_session | grep -A40 'pocketplayer' | grep -m1 -oE 'state=[A-Z_]+\([0-9]+\)')" | tee -a $OUT/steps.txt; }
alive() { echo "pid=$(adb shell pidof $PKG)" | tee -a $OUT/steps.txt; }
shot() { adb exec-out screencap -p > $OUT/$1.png; }

ffmpeg -loglevel error -y -f lavfi -i testsrc=size=1280x720:rate=30 -f lavfi -i sine=frequency=440 -t 40 \
  -c:v libx264 -b:v 6M -pix_fmt yuv420p -c:a aac -shortest /tmp/test.mp4
ffmpeg -loglevel error -y -f lavfi -i sine=frequency=660 -t 15 -c:a libmp3lame /tmp/test.mp3

adb install -r pocket-player.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell dumpsys deviceidle whitelist +$PKG || true
adb push /tmp/test.mp4 /sdcard/Movies/test.mp4
adb push /tmp/test.mp3 /sdcard/Music/test.mp3
adb shell content call --method scan_volume --uri content://media --arg external_primary || true
sleep 5
VID=$(adb shell content query --uri content://media/external/video/media --projection _id | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)
AID=$(adb shell content query --uri content://media/external/audio/media --projection _id | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)
note "video id=$VID audio id=$AID"

adb logcat -c
note "1 啟動"
adb shell am start -W -n $PKG/.MainActivity; sleep 6; shot 1-launch; alive

note "2 用隨身聽開 MP3"
adb shell am start -W -a android.intent.action.VIEW -t audio/mpeg --grant-read-uri-permission \
  -d content://media/external/audio/media/$AID -n $PKG/.MainActivity
sleep 6; shot 2-mp3; playing; alive

note "3 加入 MP4 並切過去"
adb shell am start -W -a android.intent.action.VIEW -t video/mp4 --grant-read-uri-permission \
  -d content://media/external/video/media/$VID -n $PKG/.MainActivity
sleep 3
adb shell input keyevent KEYCODE_MEDIA_NEXT
sleep 15; shot 3-mp4; playing; alive

echo "通知: $(adb shell dumpsys notification --noredact | grep -A4 'pkg=com.rcj.pocketplayer' | grep -m1 -oE 'importance=[0-9]+|channel=[a-z_]+' | tr '\n' ' ')" | tee -a $OUT/steps.txt
adb shell dumpsys notification --noredact | grep -B2 -A12 'pkg=com.rcj.pocketplayer' > $OUT/notification.txt
note "4 回桌面（背景播放）"
adb shell input keyevent KEYCODE_HOME
sleep 10; playing; alive

note "5 重新開啟"
adb shell am start -W -n $PKG/.MainActivity; sleep 6; shot 5-reopen; playing; alive

note "6 強制關閉後重新開啟（還原清單）"
adb shell am force-stop $PKG; sleep 2
adb shell am start -W -n $PKG/.MainActivity; sleep 8; shot 6-restart; alive

adb logcat -d > $OUT/logcat.txt
{ grep -nE "FATAL EXCEPTION|AndroidRuntime|ExoPlayerImplInternal|MediaSessionService|$PKG" $OUT/logcat.txt | grep -vE "^.*D/" | head -300; } > $OUT/important.txt
echo "閃退次數: $(grep -c 'FATAL EXCEPTION' $OUT/logcat.txt)" | tee -a $OUT/steps.txt
echo "播放器操作失敗: $(grep -c 'Session operation failed' $OUT/logcat.txt)" | tee -a $OUT/steps.txt
grep -E "Caused by|pocketplayer\.[A-Za-z]+\.|E ExoPlayer" $OUT/logcat.txt | head -40 > $OUT/causes.txt
exit 0
