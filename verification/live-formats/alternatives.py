"""Check same-channel variants and isolate the audio codec on identical video."""
import functools
import http.server
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import time

from probe import api, decode, emit, media_url, ProbeError, sample, words
from simulator import run


def variants():
    cats = api("get_live_categories")
    matches = {}
    for group in cats:
        if "peru" not in words(group.get("category_name")).split():
            continue
        category = str(group.get("category_id", ""))
        if not category.isdigit():
            continue
        for row in api("get_live_streams", category_id=category):
            label = words(row.get("name", "")).replace("golperu", "gol peru")
            if not re.fullmatch(r"(?:(?:pe|per|peru) )?gol peru(?: (?:tv|m|l|w|sd|hd|fhd|uhd|4k|[0-9]{1,4}p?))*", label):
                continue
            identifier = str(row.get("stream_id", ""))
            if identifier.isdigit() and len(identifier) <= 14:
                matches[identifier] = label
    emit(stage="variants", candidates=matches)
    if "878" not in matches:
        raise ProbeError("original_changed")
    return {k: matches[k] for k in sorted(matches, key=lambda n: n != "878")[:4]}


class QuietServer(http.server.SimpleHTTPRequestHandler):
    extensions_map = {".m3u8": "application/vnd.apple.mpegurl", ".ts": "video/mp2t",
                      ".mp4": "video/mp4", ".m4s": "video/iso.segment"}

    def do_GET(self):
        if self.path == "/negotiated.ts":
            agent = self.headers.get("User-Agent", "")
            requested = self.headers.get("Range", "")
            shape = requested if requested in ("bytes=0-1", "bytes=0-0", "bytes=0-", "") else "other"
            emit(stage="apple_request_shape", core_media="AppleCoreMedia/" in agent,
                 mobile=bool(re.search(r"iPhone|iPad|iPod|iOS", agent, re.I)), byte_range=shape)
            self.send_response(307)
            self.send_header("Location", "/aac/index.m3u8")
            self.send_header("Content-Length", "0")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            return
        super().do_GET()

    def log_message(self, format, *args):
        pass


def local_comparison(data, root):
    source = root / "original.bin"
    source.write_bytes(data)
    signatures = {}
    for variant in ("original", "aac"):
        target = root / "media" / variant
        target.mkdir(parents=True)
        audio = ["-c:a", "copy"] if variant == "original" else ["-c:a", "aac", "-b:a", "128k"]
        ff = ["ffmpeg", "-v", "error", "-nostdin", "-y", "-protocol_whitelist", "file,pipe"]
        subprocess.run([*ff, "-i", str(source), "-map", "0:v:0", "-map", "0:a:0",
                        "-c:v", "copy", *audio, "-t", "12", "-hls_time", "2",
                        "-hls_playlist_type", "vod", "-hls_list_size", "0",
                        "-hls_segment_filename", str(target / "part%03d.ts"),
                        str(target / "index.m3u8")], capture_output=True, check=True, timeout=40)
        signatures[variant] = subprocess.check_output(
            [*ff, "-i", str(target / "index.m3u8"), "-map", "0:v:0", "-c:v", "copy",
             "-f", "streamhash", "-hash", "sha256", "pipe:1"], stderr=subprocess.DEVNULL, timeout=22)
    equal = bool(signatures["original"].strip()) and signatures["original"] == signatures["aac"]
    emit(stage="matched_video", identical_compressed_video=equal)
    if not equal:
        raise ProbeError("comparison_video_mismatch")
    aac = ["-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "48000"]
    for variant in ("fragmented", "progressive"):
        target = root / "media" / variant
        target.mkdir(exist_ok=True)
        if variant == "fragmented":
            video = ["-c:v", "copy"]
            packaging = ["-hls_segment_type", "fmp4", "-hls_fmp4_init_filename", "init.mp4"]
            suffix = "m4s"
        else:
            video = ["-vf", "bwdif=mode=send_frame,scale=1280:720,setsar=1,format=yuv420p",
                     "-r", "30000/1001", "-c:v", "libx264", "-profile:v", "main", "-level:v", "3.1",
                     "-preset", "veryfast", "-g", "60", "-keyint_min", "60", "-sc_threshold", "0",
                     "-b:v", "2500k", "-maxrate", "3000k", "-bufsize", "6000k"]
            packaging = ["-hls_flags", "independent_segments"]
            suffix = "ts"
        process = subprocess.run([*ff, "-i", str(source), "-map", "0:v:0", "-map", "0:a:0", *video, *aac,
                                  "-t", "12", "-hls_time", "2", "-hls_playlist_type", "vod", *packaging,
                                  "-hls_segment_filename", str(target / ("part%03d." + suffix)),
                                  str(target / "index.m3u8")], capture_output=True, timeout=75)
        emit(stage="comparison_packaging", variant=variant, prepared=process.returncode == 0)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0),
        functools.partial(QuietServer, directory=str(root / "media")))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def main():
    selected = variants()
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        server = None
        for identifier, label in selected.items():
            for attempt in (1, 2):
                try:
                    data, response = sample(media_url(identifier, "m3u8"))
                    result = decode(data)
                    emit(stage="variant_decode", public_id=identifier, label=label,
                         attempt=attempt, **response, **result)
                    if (server is None and not response["fallback"] and result["video_frames"] and result["audio_frames"]
                            and any(t.get("codec_name") == "mp2" for t in result["tracks"])):
                        try:
                            server = local_comparison(data, root)
                            emit(stage="matched_source", public_id=identifier)
                        except Exception as ex:
                            emit(stage="matched_video", error=type(ex).__name__)
                    break
                except Exception as ex:
                    emit(stage="variant_decode", public_id=identifier, attempt=attempt,
                         error=str(ex) if isinstance(ex, ProbeError) else type(ex).__name__)
                    if attempt == 1:
                        time.sleep(2)
            if server:
                break
        try:
            run(alternatives=selected, port=server.server_port if server else None)
        finally:
            if server:
                server.shutdown()
                server.server_close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        emit(stage="variant_setup", error=str(error) if isinstance(error, ProbeError) else type(error).__name__)
        raise SystemExit(1)
