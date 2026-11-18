"""Bounded playback verification with the existing test account. Report scalars only."""
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ORIGIN = "https://api.snaptvnow.com"
ACCOUNT = "dieguito2026"
SAMPLE_LIMIT = 4 * 1024**2
MANIFEST_LIMIT = 512 * 1024
FORMATS = ("m3u8", "ts")
TARGETS = ("gol peru", "canal n")
UA = "SNAPTVNOW-LiveFormatCheck/1.0"


class ProbeError(Exception):
    pass


def emit(**fields):
    print(json.dumps(fields, ensure_ascii=True, separators=(",", ":")), flush=True)


def own_url(value, base=ORIGIN):
    url = urllib.parse.urljoin(base, value)
    parts = urllib.parse.urlsplit(url)
    if (parts.scheme != "https" or parts.hostname != "api.snaptvnow.com"
            or parts.port not in (443, None) or parts.username or parts.password):
        raise ProbeError("unexpected_media_origin")
    return url


class OwnRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return super().redirect_request(req, fp, code, msg, headers, own_url(newurl, req.full_url))


opener = urllib.request.build_opener(OwnRedirect)


def open_url(url, *, form=None, byte_range=None):
    headers = {"User-Agent": UA, "Accept-Encoding": "identity"}
    if byte_range:
        headers["Range"] = byte_range
    data = urllib.parse.urlencode(form).encode() if form is not None else None
    req = urllib.request.Request(own_url(url), data=data, headers=headers)
    try:
        return opener.open(req, timeout=22)
    except urllib.error.HTTPError as ex:
        with ex:
            raise ProbeError(f"http_{ex.code}") from None
    except (OSError, urllib.error.URLError):
        raise ProbeError("connection_error") from None


def api(action, **extra):
    form = {"username": ACCOUNT, "password": os.environ["DIEGUITO2026_PASSWORD"],
            "action": action, **extra}
    with open_url(ORIGIN + "/player_api.php", form=form) as response:
        buf = response.read(18 * 1024**2 + 1)
    if len(buf) > 18 * 1024**2:
        raise ProbeError("catalog_limit")
    return json.loads(buf)


def words(name):
    text = unicodedata.normalize("NFKD", str(name)).encode("ascii", "ignore").decode().lower()
    return " ".join(re.findall(r"[a-z0-9]+", text))


def channels():
    groups = api("get_live_categories")
    if not isinstance(groups, list):
        raise ProbeError("catalog_denied")
    peru = [g for g in groups if "peru" in words(g.get("category_name")).split()
            and str(g.get("category_id", "")).isdigit()]
    peru.sort(key=lambda g: words(g["category_name"]) != "peru")
    if not peru:
        raise ProbeError("peru_category_missing")
    matches = {target: {} for target in TARGETS}
    for group in peru[:4]:
        rows = api("get_live_streams", category_id=str(group["category_id"]))
        if not isinstance(rows, list):
            raise ProbeError("channel_list_unavailable")
        for row in rows:
            identifier = str(row.get("stream_id", ""))
            name = re.sub(r"\bgolperu\b", "gol peru", words(row.get("name", "")))
            if not identifier.isdigit() or len(identifier) > 14:
                continue
            for target in TARGETS:
                if re.match(r"^(?:(?:pe|per|peru) )?" + target + r"(?: |$)", name):
                    matches[target][identifier] = name
    selected = {}
    for target, candidates in matches.items():
        # Prefer the TV variant shown in Smarters. Preserve provider catalogue order for ties.
        ordered = sorted(candidates, key=lambda i: ("tv" not in candidates[i].split(),
                                                     len(candidates[i].split())))
        if ordered:
            selected[target] = ordered[0]
        emit(case=target, stage="selection", matches=len(ordered),
             tv_selected=bool(ordered and "tv" in candidates[ordered[0]].split()),
             public_id=int(ordered[0]) if ordered else None)
    if TARGETS[0] not in selected:
        raise ProbeError("target_missing")
    return selected


def media_url(identifier, fmt):
    password = urllib.parse.quote(os.environ["DIEGUITO2026_PASSWORD"], safe="")
    return f"{ORIGIN}/live/{ACCOUNT}/{password}/{identifier}.{fmt}"


def kind(buf):
    if buf.lstrip(b"\xef\xbb\xbf\r\n\t ").startswith(b"#EXTM3U"):
        return "hls"
    if len(buf) > 376 and any(buf[k] == buf[k+188] == buf[k+376] == 0x47
                              for k in range(min(188, len(buf)-376))):
        return "mpegts"
    if buf[4:8] in (b"ftyp", b"styp", b"moof", b"moov", b"sidx"):
        return "mp4"
    return "other"


def get(url, budget, *, byte_range=None):
    start = time.monotonic()
    with open_url(url, byte_range=byte_range) as response:
        first = response.read(1024)
        fmt = kind(first)
        limit = min(budget, MANIFEST_LIMIT) if fmt == "hls" else budget
        body = bytearray(first)
        while len(body) < limit and time.monotonic() - start < 20:
            chunk = response.read(min(65536, limit - len(body)))
            if not chunk:
                break
            body.extend(chunk)
        mime = response.headers.get_content_type()
        return bytes(body), {
            "status": response.status, "mime": mime if re.fullmatch(r"[a-z0-9.+-]+/[a-z0-9.+-]+", mime) else "other",
            "payload": fmt, "fallback": response.headers.get("X-SMN-Live-Fallback") == "next-channel",
            "elapsed_ms": round((time.monotonic() - start) * 1000),
        }, own_url(response.geturl())


def children(playlist, base):
    return [own_url(line.strip(), base) for line in playlist.splitlines()
            if line.strip() and not line.startswith("#")]


def sample(url):
    buf, meta, base = get(url, SAMPLE_LIMIT)
    if meta["payload"] != "hls":
        return buf, meta
    meta["master_levels"] = 0
    for _ in range(3):
        playlist = buf.decode("utf-8-sig")
        rows = children(playlist, base)
        if not rows:
            raise ProbeError("empty_playlist")
        if "#EXT-X-STREAM-INF:" not in playlist:
            break
        meta["master_levels"] += 1
        buf, _, base = get(rows[0], MANIFEST_LIMIT)
    else:
        raise ProbeError("playlist_depth")
    meta["segments"] = len(rows)
    meta["duration_tag"] = "#EXTINF:" in playlist
    meta["byte_ranges"] = "#EXT-X-BYTERANGE" in playlist
    if re.search(r"#EXT-X-KEY:.*\bMETHOD=(?!NONE\b)", playlist) or meta["byte_ranges"]:
        raise ProbeError("sample_requires_native_playlist")
    prefix = b""
    initialization = re.search(r'^#EXT-X-MAP:[^\r\n]*?URI="([^"]+)"', playlist, re.M)
    if initialization:
        prefix, _, _ = get(own_url(initialization[1], base), MANIFEST_LIMIT)
    segments = rows[max(0, len(rows)-3):][:2]
    sample_bytes = bytearray(prefix)
    for segment in segments:
        if len(sample_bytes) >= SAMPLE_LIMIT:
            break
        data, smeta, _ = get(segment, SAMPLE_LIMIT-len(sample_bytes))
        sample_bytes.extend(data)
        meta["segment_payload"] = smeta["payload"]
        meta["segment_status"] = smeta["status"]
        meta["segment_mime"] = smeta["mime"]
    return bytes(sample_bytes), meta


def decode(sample_bytes):
    with tempfile.TemporaryDirectory() as tmp:
        path = str(Path(tmp) / "sample.bin")
        Path(path).write_bytes(sample_bytes)
        inp = ["-v", "error", "-protocol_whitelist", "file,pipe", "-i", path]
        fields = ("codec_type", "codec_name", "profile", "level", "width", "height", "pix_fmt",
                  "field_order", "r_frame_rate", "channels", "sample_rate")
        streams = subprocess.run(["ffprobe", *inp, "-show_entries",
                                  "stream=" + ",".join(fields), "-of", "json"],
                                 capture_output=True, timeout=22)
        info = json.loads(streams.stdout or b"{}").get("streams", [])
        tracks = [{k: v for k, v in t.items() if k in fields}
                  for t in info if t.get("codec_type") in ("audio", "video")][:6]
        decoded = {}
        for index, media in (("v", "video"), ("a", "audio")):
            proc = subprocess.run(["ffmpeg", "-nostdin", *inp, "-map", f"0:{index}:0",
                                   "-threads", "1", "-t", "2", "-f", "framemd5", "pipe:1"],
                                  capture_output=True, timeout=22)
            frames = [l for l in proc.stdout.splitlines() if re.match(rb"^\d+,", l)]
            decoded[media + "_frames"] = len(frames)
        return {"bytes": len(sample_bytes), "tracks": tracks, **decoded}


def run():
    if not os.environ.get("DIEGUITO2026_PASSWORD"):
        raise ProbeError("test_access_unavailable")
    ids = channels()
    if len(sys.argv) == 3 and sys.argv[1] == "selection":
        Path(sys.argv[2]).write_text(json.dumps(ids), encoding="utf-8")
        return
    for name, identifier in ids.items():
        for fmt in FORMATS:
            try:
                data, headers = sample(media_url(identifier, fmt))
                emit(case=name, stage="delivery", request=fmt, **headers, **decode(data))
            except ProbeError as ex:
                emit(case=name, stage="delivery", request=fmt, error=str(ex))
            except Exception as ex:
                emit(case=name, stage="delivery", request=fmt, error=type(ex).__name__)
            time.sleep(2)


if __name__ == "__main__":
    try:
        run()
    except Exception as error:
        emit(stage="setup", error=str(error) if isinstance(error, ProbeError) else type(error).__name__)
        sys.exit(1)
