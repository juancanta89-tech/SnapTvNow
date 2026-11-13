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


if __name__ == "__main__":
    directory=Path(os.environ["RUNNER_TEMP"])/"snaptvnow-native-probe"
    directory.mkdir(parents=True,exist_ok=True)
    try:
        prepare_apk(directory)
        print("Original APK identity and signature verified.")
    except CheckFailure as error:
        print("Preparation failed: "+str(error))
        raise SystemExit(1)
    except Exception:
        print("Preparation failed: official_download_unavailable")
        raise SystemExit(1)
