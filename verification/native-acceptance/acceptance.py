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
    output = command(["adb", "shell", shlex.join(parameters)], timeout=100)
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_RESULT: native_report="):
            result = json.loads(line.partition("native_report=")[2])
            allowed = {"mode", "status", "reason", "title_selection_checked", "moving_image_samples",
                       "audio_track_active", "first_image_epoch_ms", "observation_end_epoch_ms",
                       "observation_duration_ms", "origin_attempt_selection", "start_action_sent"}
            return {key: value for key, value in result.items() if key in allowed}
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


def series_target(data):
    if not isinstance(data, dict) or not isinstance(data.get("info"), dict):
        raise CheckFailure("series_detail_not_verified")
    title = safe_title(data["info"].get("name"))
    seasons = data.get("episodes")
    if not isinstance(seasons, dict):
        raise CheckFailure("series_episodes_not_verified")
    episodes = []
    for season, values in seasons.items():
        if not isinstance(values, list):
            raise CheckFailure("series_episodes_not_verified")
        for episode in values:
            if not isinstance(episode, dict):
                raise CheckFailure("series_episodes_not_verified")
            episode_id = str(episode.get("id", ""))
            if not episode_id.isdigit():
                raise CheckFailure("episode_reference_not_verified")
            try:
                season_number = int(episode.get("season", season))
                episode_number = int(episode.get("episode_num", 0))
            except (TypeError, ValueError):
                raise CheckFailure("episode_order_not_verified") from None
            episodes.append((season_number, episode_number, episode_id, safe_title(episode.get("title"))))
    if not episodes:
        raise CheckFailure("series_has_no_observed_episodes")
    first = min(episodes)
    if sum(episode[3] == first[3] for episode in episodes) != 1:
        raise CheckFailure("ambiguous_episode_title")
    return {"resource": "30725", "kind": "series", "title": title,
            "episode_id": first[2], "episode_title": first[3],
            "episode_selection": "first_by_season_and_episode"}


def audio_evidence(path, captured_at, native):
    first = native.get("first_image_epoch_ms", 0) / 1000
    end = native.get("observation_end_epoch_ms", 0) / 1000
    if first <= 0 or end - first < 4:
        return {"audio_signal": "not_verified"}
    try:
        with wave.open(str(path), "rb") as source:
            if source.getnchannels() != 1 or source.getsampwidth() != 2:
                return {"audio_signal": "not_verified"}
            rate = source.getframerate()
            start_frame = max(0, int((first - captured_at + 1) * rate))
            source.setpos(min(start_frame, source.getnframes()))
            frame_count = max(0, int((end - first - 1.5) * rate))
            samples = array.array("h", source.readframes(frame_count))
        if len(samples) < rate * 2:
            return {"audio_signal": "not_verified"}
        rms = math.sqrt(sum(value * value for value in samples) / len(samples)) / 32768
        return {"audio_signal": "observed" if rms > 0.001 else "not_observed",
                "audio_rms": round(rms, 6), "audio_sample_seconds": round(len(samples) / rate, 2)}
    except (OSError, ValueError, wave.Error):
        return {"audio_signal": "not_verified"}


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
        report["login"] = probe("login", password=password)
        if report["login"].get("status") != "login_observed":
            raise CheckFailure("native_login_not_observed")
        # Metadata only. Media is requested exclusively by the installed app after UI selection.
        try:
            targets = live_targets(catalog("get_live_streams", password))
        except CheckFailure as error:
            targets = [{"resource": public_id, "kind": "live", "state": str(error)}
                       for public_id in ("884", "394")]
        try:
            targets.append(series_target(catalog("get_series_info", password, "30725")))
        except CheckFailure as error:
            targets.append({"resource": "30725", "kind": "series", "state": str(error)})
        for target in targets:
            if "state" in target:
                report["cases"].append(target)
                continue
            audio_path = directory / "native-audio.wav"
            captured_at = time.time()
            recorder = subprocess.Popen(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "pulse",
                 "-i", "snaptvnow_test.monitor", "-ac", "1", "-ar", "16000", str(audio_path)],
                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            native = {}
            try:
                native = probe(target["kind"], title=target["title"],
                               episode_title=target.get("episode_title"))
            except CheckFailure as error:
                if str(error) == "native_command_timeout":
                    raise
                native = {"status": "not_verified", "reason": str(error)}
            finally:
                recorder.terminate()
                try:
                    recorder.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    recorder.kill()
                    recorder.wait()
            sound = audio_evidence(audio_path, captured_at, native)
            audio_path.unlink(missing_ok=True)
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
