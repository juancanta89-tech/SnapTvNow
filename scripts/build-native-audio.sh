#!/usr/bin/env bash
set -euo pipefail
snap_project_root="$(cd "$(dirname "$0")/.." && pwd)"
snap_media_root="$snap_project_root/vendor/media"
snap_audio_main="$snap_media_root/libraries/decoder_ffmpeg/src/main"
snap_ffmpeg_root="$snap_audio_main/jni/ffmpeg"
[[ "$(git -C "$snap_media_root" rev-parse HEAD)" == 8c6678b657ede1e7883fc164ef73ed483c7796c3 ]]
[[ "$(git -C "$snap_ffmpeg_root" rev-parse HEAD)" == 34277e12e80031c7f89494ba543684bc1dd0be8f ]]
snap_needs_build=false
for snap_abi in armeabi-v7a arm64-v8a x86 x86_64; do
 for snap_lib in avcodec avutil swresample; do
  [[ -s "$snap_ffmpeg_root/android-libs/$snap_abi/lib$snap_lib.a" ]] || snap_needs_build=true
 done
done
[[ -s "$snap_ffmpeg_root/libavutil/avconfig.h" && -s "$snap_ffmpeg_root/libavutil/ffversion.h" ]] || snap_needs_build=true
if [[ "$snap_needs_build" == true ]]; then
 case "$(uname -s)" in Darwin) snap_host=darwin-x86_64 ;; Linux) snap_host=linux-x86_64 ;; *) exit 1 ;; esac
 bash "$snap_audio_main/jni/build_ffmpeg.sh" "$snap_audio_main" "$ANDROID_HOME/ndk/26.1.10909125" "$snap_host" 23 mp3 aac ac3 eac3 flac opus vorbis
fi
for snap_abi in armeabi-v7a arm64-v8a x86 x86_64; do
 for snap_lib in avcodec avutil swresample; do
  test -s "$snap_ffmpeg_root/android-libs/$snap_abi/lib$snap_lib.a"
 done
done
printf 'Pinned native audio libraries ready for all four Android ABIs.\n'
