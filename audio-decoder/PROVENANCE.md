# Native audio decoder

This module builds the official AndroidX Media3 FFmpeg extension at 1.11.1,
commit `8c6678b657ede1e7883fc164ef73ed483c7796c3`, against the application's exact
Media3 version. Source: https://github.com/androidx/media/tree/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/decoder_ffmpeg

FFmpeg is the official n6.1.4 source, commit
`34277e12e80031c7f89494ba543684bc1dd0be8f`:
https://github.com/FFmpeg/FFmpeg/tree/34277e12e80031c7f89494ba543684bc1dd0be8f

CI verifies both checked-out commits before building. The source build enables
only audio decoders mp3 (including MP1/MP2), aac, ac3, eac3, flac, opus and vorbis,
with avcodec/avutil/swresample. No network protocols, demuxers, video decoders,
GPL or nonfree components are enabled. No downloaded prebuilt audio binaries
are trusted. `scripts/build-native-audio.sh` and the official upstream build
script reproduce the static inputs and JNI library for four Android ABIs.

Media3 extension: Apache-2.0. FFmpeg configuration: LGPL-2.1-or-later. Preserve
upstream copyright/license files and the corresponding source/build references
when distributing. This repository also contains the app's build integration.
The existing OpenVPN engine has its separate license documented in README.md.

Android instrumented tests decode synthetic MP2/AAC/AC3/EAC3 frames through the
actual JNI decoder and require non-silent PCM. They do not certify any live
provider signal, physical TV, HDMI receiver, or that every 1080 label is real HD.
