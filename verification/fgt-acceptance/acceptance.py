#!/usr/bin/env python3
"""Two bounded UI checks in the original FGT app; no direct media requests."""

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


APK_SOURCE = "http://51.222.111.161/APK/snaptvnow.apk"
APK_SHA256 = "7011b450f4d8a28fea7ce3cb482fc76e0ede1e58fa607abac56e2ae306fa34bd"
CERT_SHA256 = "c77b3decb2ecbe6274ace7a6970b56aa3b803fb1bfef56b711288c5cd48c3d52"
PACKAGE = "boxbr.apksrebrand.smarters.nointro"
USERNAME = "dieguito2026"
API = "https://api.snaptvnow.com/player_api.php"
VERSION = "3.0.9.1"
VERSION_CODE = 110
MAX_ARCHIVE = 110 * 1024 * 1024
MAX_CATALOG = 8 * 1024 * 1024


class CheckFailure(Exception):
    """Only fixed, non-sensitive error codes may reach the job output."""


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


def prepare_apk(directory):
    # Exact official Downloader 385212 artifact already identified by the owner.
    # Fail on any change instead of installing an unknown build or re-signing it.
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(APK_SOURCE, timeout=90) as response:
        apk_bytes = limited_read(response, MAX_ARCHIVE)
    if hashlib.sha256(apk_bytes).hexdigest() != APK_SHA256:
        raise CheckFailure("official_fgt_apk_changed")
    apk = directory / "official.apk"
    apk.write_bytes(apk_bytes)
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    certificate = command([str(tools / "apksigner"), "verify", "--print-certs", str(apk)])
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", certificate)
    if not digests or digests[0].lower() != CERT_SHA256:
        raise CheckFailure("official_fgt_signature_mismatch")
    badging = command([str(tools / "aapt"), "dump", "badging", str(apk)])
    expected = f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' versionName='{VERSION}'"
    if not badging.startswith(expected):
        raise CheckFailure("official_apk_identity_mismatch")
    evidence = {"package": PACKAGE, "version": VERSION, "version_code": VERSION_CODE,
                "official_downloader_code": "385212",
                "apk_sha256": hashlib.sha256(apk_bytes).hexdigest(),
                "signature_verified": True, "playback_attempts": 0,
                "state": "prepared", "source_status": "existing_official_published_fgt_apk"}
    with zipfile.ZipFile(io.BytesIO(apk_bytes)) as source:
        evidence["abis"] = sorted({name.split("/")[1] for name in source.namelist()
                                    if name.startswith("lib/") and name.count("/") >= 2})
    if os.environ.get("GITHUB_OUTPUT"):
        compatible = not evidence["abis"] or bool(set(evidence["abis"]) & {"x86", "x86_64"})
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("emulator_api=" + ("28" if compatible else "30") + "\n")
            output.write("emulator_target=" + ("default" if compatible else "google_apis") + "\n")
    (directory / "preparation.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def probe(mode, password=None, title=None, episode_title=None, episode_index=None):
    native_mode = {"login": "fgt_login", "live": "fgt_live"}.get(mode, mode)
    parameters = ["am", "instrument", "-w", "-r", "-e", "mode", native_mode]
    if mode == "series" and os.environ.get("NATIVE_COLD_SERIES") == "true":
        parameters.extend(["-e", "pre_play_wait_ms", "35000"])
    for key, value in (("password", password), ("title", title), ("episode_title", episode_title), ("episode_index", episode_index)):
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
        output = command(["adb", "shell", shlex.join(parameters)], timeout=125)
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
                       "pre_play_wait_ms", "episode_picker_observed", "controls", "component",
                       "login_submission_actions"}
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
    for public_id in ("1344", "1412"):
        matches = [row for row in rows if isinstance(row, dict)
                   and str(row.get("stream_id", "")) == public_id]
        if len(matches) != 1:
            targets.append({"resource": public_id, "kind": "live", "state": "reference_not_verified"})
            continue
        title = safe_title(matches[0].get("name"))
        if sum(isinstance(row, dict) and str(row.get("name", "")).strip() == title for row in rows) != 1:
            targets.append({"resource": public_id, "kind": "live", "state": "ambiguous_catalog_title"})
            continue
        target = {"resource": public_id, "kind": "live", "title": title}
        # Only numeric public/source references may leave the normal catalogue read.
        profile = matches[0].get("smn_profile")
        if isinstance(profile, dict):
            upstream = str(profile.get("upstreamId", profile.get("upstream_id", "")))
            server = str(profile.get("server", ""))
            if upstream.isdigit() and server in ("ccf",):
                target["saved_reference"] = {"public_id": public_id, "server": server,
                                              "upstream_id": upstream}
        targets.append(target)
    return targets


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
    device_abis = command(["adb", "shell", "getprop ro.product.cpu.abilist"]).strip().split(",")
    if preparation["abis"] and not set(preparation["abis"]) & set(device_abis):
        raise CheckFailure("official_fgt_abi_not_available_on_runner")
    command(["adb", "install", "-g", str(directory / "official.apk")])
    command(["adb", "install", str(directory / "native-probe.apk")])
    report = {"app": preparation, "test_scope": "authorized_test_account",
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
        # Metadata only. Media is requested exclusively by the installed app after UI selection.
        targets = []
        if os.environ.get("NATIVE_SCOPE", "all") != "series":
            try:
                targets = live_targets(catalog("get_live_streams", password))
            except CheckFailure as error:
                targets = [{"resource": public_id, "kind": "live", "state": str(error)}
                           for public_id in ("1344", "1412")]
        # This exact episode has already passed native playback twice. Check its
        # parent catalogue once; do not create another playback for that sample.
        started = time.monotonic()
        try:
            detail = catalog("get_series_info", password, "3565")
            episodes = detail.get("episodes", {}) if isinstance(detail, dict) else {}
            matches = [episode for season in episodes.values() if isinstance(season, list)
                       for episode in season if isinstance(episode, dict) and str(episode.get("id")) == "30725"]
            report["series_catalogue"] = {"series_id": "3565", "episode_id": "30725",
                                           "exact_episode_present": len(matches) == 1,
                                           "duration_ms": round((time.monotonic()-started)*1000),
                                           "playback_attempts": 0}
        except CheckFailure as error:
            report["series_catalogue"] = {"state": "not_verified", "reason": str(error), "playback_attempts": 0}
        report["login"] = probe("login", password=password)
        report["login"].pop("_audio_markers", None)
        if report["login"].get("status") != "login_observed":
            raise CheckFailure("native_login_not_observed")
        for target_index, target in enumerate(targets):
            if "state" in target:
                report["cases"].append(target)
                continue
            native = {}
            try:
                native = probe(target["kind"], title=target["title"],
                               episode_title=target.get("episode_title"), episode_index=target.get("episode_index"))
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
            if str(native.get("reason", "")).startswith("fgt_"):
                for pending in targets[target_index+1:]:
                    report["cases"].append(dict(pending, state="not_verified",
                                                 reason="same_native_control_not_verified"))
                break
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
