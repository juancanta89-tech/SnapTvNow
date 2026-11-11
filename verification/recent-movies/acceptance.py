#!/usr/bin/env python3
"""Inspect two exact VOD entries and optionally observe the official Android app."""

import argparse
import array
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import shlex
import subprocess
import struct
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import unicodedata
import wave
import zipfile


REPOSITORY = "juancanta89-tech/SnapTvNow"
APP_COMMIT = "4370a0891bdf5a2efdefaa4a6df48b62d49e747a"
ARTIFACT = 13787040424
ARCHIVE_SHA256 = "292fb2e4ab5905ea0ceee3cb86ba192371b079b55ce38d433d143b82d4e93446"
PACKAGE = "com.snaptvnow.tv"
USERNAME = "dieguito2026"
API = "https://api.snaptvnow.com/player_api.php"
VERSION = "1.0.32"
VERSION_CODE = 61
MAX_ARCHIVE = 64 * 1024 * 1024
MAX_CATALOG = 40 * 1024 * 1024
REQUESTED_TITLE = "La constelación del perro (2026)"
OUTPUT_DIRECTORY = "snaptvnow-recent-movies-probe"


class CheckFailure(Exception):
    """Only fixed, non-sensitive error codes may reach the job output."""


class ArtifactRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urllib.parse.urlsplit(new_url).scheme != "https":
            raise CheckFailure("artifact_redirect_rejected")
        redirected = super().redirect_request(request, response, code, message, headers, new_url)
        if urllib.parse.urlsplit(request.full_url).netloc != urllib.parse.urlsplit(new_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        raise CheckFailure("catalog_redirect_rejected")


def command(arguments, timeout=60):
    try:
        result = subprocess.run(arguments, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        raise CheckFailure("native_command_timeout") from None
    except OSError:
        raise CheckFailure("native_command_failed") from None
    if result.returncode:
        raise CheckFailure("native_command_failed")
    return result.stdout


def limited_read(response, maximum):
    data = response.read(maximum + 1)
    if len(data) > maximum:
        raise CheckFailure("response_size_limit")
    return data


def github_request(path):
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        raise CheckFailure("artifact_read_access_missing")
    return urllib.request.Request(
        "https://api.github.com/repos/" + REPOSITORY + path,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json"})


def prepare_apk(directory):
    opener = urllib.request.build_opener(ArtifactRedirect())
    with opener.open(github_request("/commits/main"), timeout=20) as response:
        head = json.loads(limited_read(response, 1024 * 1024))
    if head.get("sha") != APP_COMMIT:
        raise CheckFailure("official_app_source_changed")
    with opener.open(github_request(f"/actions/artifacts/{ARTIFACT}/zip"), timeout=60) as response:
        archive = limited_read(response, MAX_ARCHIVE)
    if hashlib.sha256(archive).hexdigest() != ARCHIVE_SHA256:
        raise CheckFailure("official_artifact_digest_mismatch")
    with zipfile.ZipFile(io.BytesIO(archive)) as source:
        candidates = [item for item in source.infolist() if item.filename == "app-release.apk"]
        if len(candidates) != 1 or candidates[0].file_size > MAX_ARCHIVE:
            raise CheckFailure("official_apk_not_identified")
        apk_bytes = source.read(candidates[0])
    apk = directory / "official.apk"
    apk.write_bytes(apk_bytes)
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    command([str(tools / "apksigner"), "verify", str(apk)])
    badging = command([str(tools / "aapt"), "dump", "badging", str(apk)])
    expected = f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' versionName='{VERSION}'"
    if not badging.startswith(expected):
        raise CheckFailure("official_apk_identity_mismatch")
    evidence = {"package": PACKAGE, "version": VERSION, "version_code": VERSION_CODE,
                "source_commit": APP_COMMIT, "artifact_id": ARTIFACT,
                "apk_sha256": hashlib.sha256(apk_bytes).hexdigest(),
                "signature_verified": True, "playback_attempts": 0,
                "state": "prepared", "source_status": "signed_build_artifact"}
    (directory / "preparation.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def probe(mode, password=None, title=None):
    parameters = ["am", "instrument", "-w", "-r", "-e", "mode", mode]
    for key, value in (("password", password), ("title", title)):
        if value is not None:
            parameters.extend(["-e", key, str(value)])
    parameters.append("com.snaptvnow.nativeprobe/.NativeProbe")
    # adb sends one shell string. Quote every argument, including credentials and catalog titles.
    markers = []
    stop = threading.Event()
    def sample_position():
        while not stop.is_set():
            markers.append((time.time(), wav_position()))
            stop.wait(0.1)
    sampler = threading.Thread(target=sample_position, daemon=True)
    sampler.start()
    try:
        output = command(["adb", "shell", shlex.join(parameters)], timeout=100)
    finally:
        markers.append((time.time(), wav_position()))
        stop.set()
        sampler.join(timeout=1)
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_RESULT: native_report="):
            result = json.loads(line.partition("native_report=")[2])
            allowed = {"mode", "status", "reason", "title_selection_checked", "moving_image_samples",
                       "audio_track_active", "first_image_epoch_ms", "observation_end_epoch_ms",
                       "observation_duration_ms", "origin_attempt_selection", "start_action_sent",
                       "movie_resume_observed"}
            clean = {key: value for key, value in result.items() if key in allowed}
            clean["_audio_markers"] = markers
            return clean
    raise CheckFailure("native_report_missing")


def catalog(action, password, **extra):
    parameters = {"username": USERNAME, "password": password, "action": action}
    parameters.update(extra)
    request = urllib.request.Request(API + "?" + urllib.parse.urlencode(parameters),
                                     headers={"Accept": "application/json", "Cache-Control": "no-cache"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=12) as response:
            return json.loads(limited_read(response, MAX_CATALOG))
    except urllib.error.HTTPError as error:
        raise CheckFailure("catalog_http_" + str(error.code)) from None
    except (urllib.error.URLError, TimeoutError, ValueError):
        raise CheckFailure("catalog_not_verified") from None


def safe_title(value):
    if not isinstance(value, str) or not value.strip() or len(value) > 180:
        raise CheckFailure("catalog_title_not_verified")
    if any(ord(character) < 32 for character in value):
        raise CheckFailure("catalog_title_not_verified")
    if "://" in value or re.search(r"(?:username|password|token|authorization)\s*=", value, re.I):
        raise CheckFailure("catalog_title_not_verified")
    return value.strip()


def normalize_title(value):
    try:
        value = unicodedata.normalize("NFKD", safe_title(value)).casefold()
    except CheckFailure:
        return ""
    return "".join(character for character in value if character.isalnum()
                   and not unicodedata.combining(character))


def scalar_metadata(source, keys):
    if not isinstance(source, dict):
        return {}
    result = {}
    for key in keys:
        value = source.get(key)
        if isinstance(value, bool):
            continue
        if isinstance(value, (int, float)) and math.isfinite(value) and 0 <= value < 10 ** 13:
            result[key] = value
        elif isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9 .,_+()\[\]/-]{1,100}", value):
            if "://" not in value and not re.search(r"password|token|authorization", value, re.I):
                result[key] = value
    return result


def movie_details(target, password):
    try:
        details = catalog("get_vod_info", password, vod_id=target["resource"])
        if not isinstance(details, dict) or not isinstance(details.get("movie_data"), dict):
            raise CheckFailure("movie_detail_not_verified")
        movie = details["movie_data"]
        if str(movie.get("stream_id", "")) != target["resource"]:
            raise CheckFailure("movie_detail_reference_mismatch")
        info = details.get("info") if isinstance(details.get("info"), dict) else {}
        target["detail"] = {
            "state": "metadata_observed",
            "extension": scalar_metadata(movie, ("container_extension",)).get("container_extension"),
            "video": scalar_metadata(info.get("video"), ("codec_name", "profile", "width", "height",
                "pix_fmt", "bits_per_raw_sample", "bits_per_sample", "bit_rate", "duration",
                "format", "codec_tag_string", "color_primaries")),
            "audio": scalar_metadata(info.get("audio"), ("codec_name", "profile", "channels",
                "sample_rate", "sample_fmt", "bits_per_raw_sample", "bits_per_sample", "bit_rate",
                "duration", "format", "codec_tag_string")),
            "format": scalar_metadata(info, ("duration_secs", "duration", "bitrate", "bit_rate", "format")),
        }
    except CheckFailure as error:
        target["detail"] = {"state": "not_verified", "reason": str(error)}
    return target


def recent_movie_targets(password):
    categories = catalog("get_vod_categories", password)
    rows = catalog("get_vod_streams", password)
    if not isinstance(categories, list) or not isinstance(rows, list):
        raise CheckFailure("movie_catalog_not_verified")
    wanted = normalize_title(REQUESTED_TITLE)
    candidates = [row for row in rows if isinstance(row, dict)
                  and isinstance(row.get("name"), str) and normalize_title(row["name"]) == wanted]
    if len(candidates) != 1:
        return {"state": "not_verified", "reason": "exact_movie_title_not_unique",
                "exact_matches": len(candidates), "targets": []}
    first = candidates[0]
    category_id = str(first.get("category_id", ""))
    category = [row for row in categories if isinstance(row, dict)
                and str(row.get("category_id", "")) == category_id]
    if not category_id.isdigit() or len(category) != 1:
        raise CheckFailure("movie_category_not_verified")
    category_name = safe_title(category[0].get("category_name"))
    title_counts = {}
    for row in rows:
        if isinstance(row, dict) and isinstance(row.get("name"), str):
            title = row["name"].strip()
            title_counts[title] = title_counts.get(title, 0) + 1
    same_category = [row for row in rows if isinstance(row, dict)
                     and str(row.get("category_id", "")) == category_id]
    valid = [row for row in same_category if str(row.get("stream_id", "")).isdigit()
             and isinstance(row.get("name"), str) and title_counts.get(row["name"].strip()) == 1]
    if first not in valid:
        raise CheckFailure("movie_native_title_not_unique")
    def order(row):
        added = str(row.get("added", "0"))
        return (int(added) if added.isdigit() else 0, int(row["stream_id"]))
    others = sorted((row for row in valid if row is not first), key=order, reverse=True)
    selected = [first] + others[:1]
    targets = []
    for row in selected:
        target = {"resource": str(row["stream_id"]), "kind": "movie", "title": safe_title(row["name"]),
                  "category_id": category_id, "category_name": category_name,
                  "listing_extension": scalar_metadata(row, ("container_extension",)).get("container_extension")}
        targets.append(movie_details(target, password))
    return {"state": "metadata_observed", "requested_section": "Recién agregados",
            "category_name": category_name, "category_id": category_id,
            "category_movies": len(same_category), "targets": targets}


def metadata(directory):
    password = os.environ.get("DIEGUITO2026_PASSWORD", "")
    if not password:
        raise CheckFailure("secure_login_not_bound")
    report = {"checked_epoch_ms": int(time.time() * 1000), "requested_title": REQUESTED_TITLE,
              "test_account": USERNAME, "media_requests": 0}
    try:
        report.update(recent_movie_targets(password))
    except CheckFailure as error:
        report.update(state="not_verified", reason=str(error), targets=[])
    (directory / "movie-metadata.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report["state"] != "metadata_observed":
        raise CheckFailure("movie_metadata_incomplete")


def verify_current_targets(targets, password):
    rows = catalog("get_vod_streams", password)
    if not isinstance(rows, list):
        raise CheckFailure("movie_catalog_not_verified")
    for target in targets:
        matches = [row for row in rows if isinstance(row, dict)
                   and str(row.get("stream_id", "")) == target["resource"]]
        if (len(matches) != 1 or matches[0].get("name", "").strip() != target["title"]
                or str(matches[0].get("category_id", "")) != target["category_id"]):
            raise CheckFailure("movie_reference_changed")
        if sum(isinstance(row, dict) and row.get("name", "").strip() == target["title"]
               for row in rows) != 1:
            raise CheckFailure("movie_native_title_not_unique")


def safe_player_diagnostics(raw):
    # Only fixed fields derived from the official app's bounded logcat are exported.
    exceptions = ("InvalidResponseCodeException", "UnrecognizedInputFormatException", "ParserException",
                  "DecoderInitializationException", "DecoderQueryException", "MediaCodecDecoderException",
                  "DecoderException", "ExoPlaybackException", "PlaybackException", "SocketTimeoutException",
                  "UnknownHostException", "ConnectException", "SSLHandshakeException", "EOFException",
                  "FileNotFoundException", "IOException")
    mimes = ("video/avc", "video/hevc", "video/av01", "video/x-vnd.on2.vp9", "video/x-vnd.on2.vp8",
             "video/mp4v-es", "video/3gpp", "video/mpeg2", "video/dolby-vision", "audio/mp4a-latm",
             "audio/ac3", "audio/eac3", "audio/vnd.dts", "audio/flac", "audio/opus", "audio/vorbis",
             "video/x-matroska", "application/x-matroska", "video/mp4")
    codes = sorted({int(value) for value in re.findall(r"\bplayer_error_([0-9]{4,6})\b", raw)})
    http = sorted({int(value) for value in re.findall(
        r"(?:Response code:\s*|responseCode\s*[=:]\s*|statusCode\s*[=:]\s*|HTTP/[12](?:\.[01])?\s+)([1-5][0-9]{2})\b", raw)})
    decoders = sorted(set(re.findall(r"\b(?:OMX\.google\.[A-Za-z0-9_.-]{1,75}|c2\.android\.[A-Za-z0-9_.-]{1,75})\b", raw)))
    return {"state": "logcat_observed", "player_error_codes": codes,
            "exception_classes": [name for name in exceptions if re.search(r"\b" + name + r"\b", raw)],
            "http_status_codes": http, "mime_types": [name for name in mimes if name in raw],
            "decoder_names": decoders}


def begin_player_diagnostics():
    pid = command(["adb", "shell", "pidof", PACKAGE], timeout=10).strip()
    if not re.fullmatch(r"[0-9]{1,8}", pid):
        raise CheckFailure("official_app_pid_not_verified")
    # The test runner is isolated. Start a fresh log window for this exact movie.
    command(["adb", "logcat", "-c"], timeout=10)
    return pid


def player_diagnostics(pid):
    if not re.fullmatch(r"[0-9]{1,8}", pid):
        return {"state": "not_verified"}
    try:
        raw = command(["adb", "logcat", "-d", "--pid=" + pid, "-v", "brief", "-t", "256"], timeout=10)
        return safe_player_diagnostics(raw[-131072:])
    except CheckFailure:
        return {"state": "not_verified"}


def wav_position():
    try:
        return max(44, Path(os.environ["QEMU_WAV_PATH"]).stat().st_size)
    except (KeyError, OSError):
        return 44


def wav_segment(path, start_offset, end_offset, minimum_seconds=2):
    try:
        with open(path, "rb") as source:
            header = source.read(44)
            if (len(header) != 44 or header[:4] != b"RIFF" or header[8:16] != b"WAVEfmt "
                    or header[36:40] != b"data"):
                return {"audio_signal": "not_verified"}
            format_code, channels, rate = struct.unpack_from("<HHI", header, 20)
            block, bits = struct.unpack_from("<HH", header, 32)
            if format_code != 1 or channels not in (1, 2) or rate != 44100 or bits != 16 or block != channels * 2:
                return {"audio_signal": "not_verified"}
            start = 44 + max(0, int(start_offset) - 44) // block * block
            end = 44 + max(0, int(end_offset) - 44) // block * block
            if end - start > 16 * 1024 * 1024 or end <= start:
                return {"audio_signal": "not_verified"}
            source.seek(start)
            samples = array.array("h", source.read(end - start))
        seconds = len(samples) / (rate * channels)
        if seconds < minimum_seconds:
            return {"audio_signal": "not_verified", "audio_sample_seconds": round(seconds, 2)}
        rms = math.sqrt(sum(value * value for value in samples) / len(samples)) / 32768
        return {"audio_signal": "observed" if rms > 0.001 else "not_observed",
                "audio_rms": round(rms, 6), "audio_sample_seconds": round(seconds, 2)}
    except (KeyError, OSError, ValueError, struct.error):
        return {"audio_signal": "not_verified"}


def audio_evidence(path, markers, native):
    first = native.get("first_image_epoch_ms", 0) / 1000
    end = native.get("observation_end_epoch_ms", 0) / 1000
    if first <= 0 or end - first < 4 or not markers:
        return {"audio_signal": "not_verified"}
    start_offset = min(markers, key=lambda mark: abs(mark[0] - first - 1))[1]
    end_offset = min(markers, key=lambda mark: abs(mark[0] - end + 0.5))[1]
    return wav_segment(path, start_offset, end_offset)


def run(directory):
    password = os.environ.get("DIEGUITO2026_PASSWORD", "")
    if not password:
        raise CheckFailure("secure_login_not_bound")
    preparation = json.loads((directory / "preparation.json").read_text())
    command(["adb", "install", str(directory / "official.apk")])
    command(["adb", "install", str(directory / "native-probe.apk")])
    report = {"app": preparation, "test_account": USERNAME,
              "started_epoch_ms": int(time.time() * 1000), "playback_attempts": 0, "cases": []}
    try:
        # Observe actual emulator PCM, checking a local synthetic tone before media.
        audio_path = Path(os.environ["QEMU_WAV_PATH"])
        tone_start = wav_position()
        tone_result = probe("audio_probe")
        tone_result.pop("_audio_markers", None)
        time.sleep(0.2)
        tone = wav_segment(audio_path, tone_start, wav_position(), minimum_seconds=1)
        report["emulator_audio_path"] = {"tone_completed": tone_result.get("status") == "test_tone_completed",
                                         **tone, "verified": tone.get("audio_signal") == "observed"}
        if not report["emulator_audio_path"]["verified"]:
            raise CheckFailure("emulator_audio_path_not_verified")
        report["login"] = probe("login", password=password)
        report["login"].pop("_audio_markers", None)
        if report["login"].get("status") != "login_observed":
            raise CheckFailure("native_login_not_observed")
        # The earlier metadata step has established these exact public references.
        metadata_report = json.loads((directory / "movie-metadata.json").read_text())
        targets = metadata_report.get("targets")
        if (metadata_report.get("state") != "metadata_observed" or not isinstance(targets, list)
                or not 1 <= len(targets) <= 2):
            raise CheckFailure("movie_targets_not_verified")
        for target in targets:
            if (not isinstance(target, dict) or target.get("kind") != "movie"
                    or not str(target.get("resource", "")).isdigit()
                    or not str(target.get("category_id", "")).isdigit()):
                raise CheckFailure("movie_target_reference_not_verified")
            safe_title(target.get("title"))
        if normalize_title(targets[0]["title"]) != normalize_title(REQUESTED_TITLE):
            raise CheckFailure("requested_movie_not_verified")
        if any(target["category_id"] != targets[0]["category_id"] for target in targets):
            raise CheckFailure("movie_category_mismatch")
        report["requested_section"] = metadata_report.get("requested_section")
        report["category_name"] = metadata_report.get("category_name")
        report["selected_movies"] = len(targets)
        verify_current_targets(targets, password)
        for target in targets:
            if "state" in target:
                report["cases"].append(target)
                continue
            native = {}
            pid = ""
            case_started = int(time.time() * 1000)
            try:
                pid = begin_player_diagnostics()
                native = probe("movie", title=target["title"])
            except CheckFailure as error:
                if str(error) == "native_command_timeout":
                    raise
                native = {"status": "not_verified", "reason": str(error)}
            time.sleep(0.2)
            sound = audio_evidence(audio_path, native.pop("_audio_markers", []), native)
            case = dict(target, native=native, audio=sound, diagnostics=player_diagnostics(pid),
                        started_epoch_ms=case_started, finished_epoch_ms=int(time.time() * 1000),
                        current_reference_verified=True)
            case["state"] = ("native_playback_observed" if native.get("status") == "native_image_observed"
                             and native.get("audio_track_active") and sound["audio_signal"] == "observed"
                             else "not_verified")
            report["cases"].append(case)
            if native.get("start_action_sent"):
                report["playback_attempts"] += 1
        report["state"] = "completed"
    except CheckFailure as error:
        report["state"] = "blocked"
        report["reason"] = str(error)
    finally:
        report["finished_epoch_ms"] = int(time.time() * 1000)
        if os.environ.get("QEMU_WAV_PATH"):
            Path(os.environ["QEMU_WAV_PATH"]).unlink(missing_ok=True)
        (directory / "native-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        for package in (PACKAGE, "com.snaptvnow.nativeprobe"):
            try:
                command(["adb", "shell", "am force-stop " + package], timeout=10)
            except CheckFailure:
                report["cleanup"] = "not_verified"
        (directory / "native-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    if report["state"] != "completed" or any(case.get("state") != "native_playback_observed"
                                             for case in report["cases"]):
        raise CheckFailure("native_acceptance_incomplete")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["metadata", "prepare", "run"])
    arguments = parser.parse_args()
    directory = Path(os.environ["RUNNER_TEMP"]) / OUTPUT_DIRECTORY
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.umask(0o077)
    try:
        if arguments.mode == "metadata":
            metadata(directory)
        elif arguments.mode == "prepare":
            prepare_apk(directory)
            print("Verified the signed official APK and its identity. Playback remains pending.")
        else:
            run(directory)
            print("Native UI observation completed. Origin selection requires its separate trace.")
    except CheckFailure as error:
        print("::error::" + str(error))
        raise SystemExit(1) from None
    except Exception:
        print("::error::native_acceptance_preparation_failed")
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
