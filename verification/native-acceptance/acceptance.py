#!/usr/bin/env python3
"""Run three bounded UI checks in the official Android app and emit safe evidence."""

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
MAX_CATALOG = 8 * 1024 * 1024


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


def probe(mode, password=None, title=None, episode_title=None):
    parameters = ["am", "instrument", "-w", "-r", "-e", "mode", mode]
    for key, value in (("password", password), ("title", title), ("episode_title", episode_title)):
        if value is not None:
            parameters.extend(["-e", key, value])
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
                       "observation_duration_ms", "origin_attempt_selection", "start_action_sent"}
            clean = {key: value for key, value in result.items() if key in allowed}
            clean["_audio_markers"] = markers
            return clean
    raise CheckFailure("native_report_missing")


def catalog(action, password, series_id=None):
    parameters = {"username": USERNAME, "password": password, "action": action}
    if series_id is not None:
        parameters["series_id"] = series_id
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


def live_targets(rows):
    if not isinstance(rows, list):
        raise CheckFailure("live_catalog_not_verified")
    targets = []
    for public_id in ("884", "394"):
        matches = [row for row in rows if isinstance(row, dict)
                   and str(row.get("stream_id", "")) == public_id]
        if len(matches) != 1:
            targets.append({"resource": public_id, "kind": "live", "state": "reference_not_verified"})
            continue
        title = safe_title(matches[0].get("name"))
        if sum(isinstance(row, dict) and str(row.get("name", "")).strip() == title for row in rows) != 1:
            targets.append({"resource": public_id, "kind": "live", "state": "ambiguous_catalog_title"})
            continue
        targets.append({"resource": public_id, "kind": "live", "title": title})
    return targets


def series_target(password):
    # 30725 is the exact public episode seen on /series/, not a series-list ID.
    rows = catalog("get_series", password)
    if not isinstance(rows, list):
        raise CheckFailure("series_catalog_not_verified")
    candidates = [row for row in rows if isinstance(row, dict)
                  and "anatomía de grey" in str(row.get("name", "")).casefold()
                  and "2005" in str(row.get("name", ""))]
    if len(candidates) != 1:
        raise CheckFailure("exact_episode_parent_not_verified")
    parent = candidates[0]
    parent_id = str(parent.get("series_id", ""))
    if not parent_id.isdigit():
        raise CheckFailure("episode_parent_reference_not_verified")
    title = safe_title(parent.get("name"))
    if sum(isinstance(row, dict) and str(row.get("name", "")).strip() == title for row in rows) != 1:
        raise CheckFailure("ambiguous_series_title")
    data = catalog("get_series_info", password, parent_id)
    if not isinstance(data, dict) or not isinstance(data.get("episodes"), dict):
        raise CheckFailure("series_detail_not_verified")
    matches = []
    titles = []
    for values in data["episodes"].values():
        if not isinstance(values, list):
            raise CheckFailure("series_episodes_not_verified")
        for episode in values:
            if not isinstance(episode, dict):
                raise CheckFailure("series_episodes_not_verified")
            episode_title = safe_title(episode.get("title"))
            titles.append(episode_title)
            if str(episode.get("id", "")) == "30725":
                matches.append(episode)
    if len(matches) != 1:
        raise CheckFailure("exact_episode_reference_not_verified")
    episode = matches[0]
    episode_title = safe_title(episode.get("title"))
    if titles.count(episode_title) != 1:
        raise CheckFailure("ambiguous_episode_title")
    return {"resource": "30725", "kind": "series", "title": title,
            "series_public_id": parent_id, "episode_id": "30725",
            "episode_title": episode_title, "episode_selection": "exact_public_episode_id"}


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
        # Metadata only. Media is requested exclusively by the installed app after UI selection.
        try:
            targets = live_targets(catalog("get_live_streams", password))
        except CheckFailure as error:
            targets = [{"resource": public_id, "kind": "live", "state": str(error)}
                       for public_id in ("884", "394")]
        try:
            targets.append(series_target(password))
        except CheckFailure as error:
            targets.append({"resource": "30725", "kind": "series", "state": str(error)})
        for target in targets:
            if "state" in target:
                report["cases"].append(target)
                continue
            native = {}
            try:
                native = probe(target["kind"], title=target["title"],
                               episode_title=target.get("episode_title"))
            except CheckFailure as error:
                if str(error) == "native_command_timeout":
                    raise
                native = {"status": "not_verified", "reason": str(error)}
            time.sleep(0.2)
            sound = audio_evidence(audio_path, native.pop("_audio_markers", []), native)
            case = dict(target, native=native, audio=sound)
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
    parser.add_argument("mode", choices=["prepare", "run"])
    arguments = parser.parse_args()
    directory = Path(os.environ["RUNNER_TEMP"]) / "snaptvnow-native-probe"
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.umask(0o077)
    try:
        if arguments.mode == "prepare":
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
