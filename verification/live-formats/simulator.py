"""Build the diagnostic iPhone app and run it on a standard Apple simulator."""
import json
import os
from pathlib import Path
import platform
import plistlib
import re
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
WORK = Path(os.environ["RUNNER_TEMP"]) / "live-format-simulator"
BUNDLE = "com.snaptvnow.diagnostics.liveformats"


def command(*args, **kwargs):
    return subprocess.check_output([str(x) for x in args], text=True, **kwargs).strip()


def prepare_bundle(wait_for_boot=True):
    WORK.mkdir(exist_ok=True)
    sdk = command("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path")
    sdk_version = command("xcrun", "--sdk", "iphonesimulator", "--show-sdk-version")
    major = int(sdk_version.split(".")[0])
    devices = json.loads(command("xcrun", "simctl", "list", "devices", "available", "--json"))["devices"]
    options = []
    for runtime, models in devices.items():
        match = re.search(r"SimRuntime\.iOS-(\d+)(?:-(\d+))?", runtime)
        if not match or int(match[1]) > major:
            continue
        for model in models:
            if model.get("isAvailable") and model["name"].startswith("iPhone"):
                options.append((int(match[1]), int(match[2] or 0), model["name"], model["udid"], model.get("state")))
    if not options:
        raise RuntimeError("no_iPhone_simulator")
    _, _, name, udid, state = max(options)
    print("Using", name, "with iOS Simulator SDK", sdk_version, flush=True)
    (WORK / "device").write_text(udid)
    if wait_for_boot:
        command("xcrun", "simctl", "bootstatus", udid, "-b", timeout=300)
    elif state == "Shutdown":
        command("xcrun", "simctl", "boot", udid, timeout=90)
    app = WORK / "LiveFormats.app"
    app.mkdir(exist_ok=True)
    with (app / "Info.plist").open("wb") as handle:
        plistlib.dump({
            "CFBundleIdentifier": BUNDLE, "CFBundleExecutable": "LiveFormats",
            "CFBundleName": "LiveFormats", "CFBundlePackageType": "APPL",
            "CFBundleVersion": "1", "CFBundleShortVersionString": "1.0",
            "CFBundleSupportedPlatforms": ["iPhoneSimulator"], "MinimumOSVersion": "17.0",
            "UIDeviceFamily": [1], "LSRequiresIPhoneOS": True,
            "NSAppTransportSecurity": {"NSAllowsLocalNetworking": True},
            "UILaunchScreen": {}, "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait"]
        }, handle)
    return sdk, app, udid


def prepare():
    sdk, app, udid = prepare_bundle(wait_for_boot=False)
    command("xcrun", "--sdk", "iphonesimulator", "swiftc", "-swift-version", "5",
            "-sdk", sdk, "-target", platform.machine()+"-apple-ios17.0-simulator",
            "-parse-as-library", HERE/"NativePlayback.swift", "-o", app/"LiveFormats", timeout=300)
    command("codesign", "--force", "--sign", "-", app)
    command("xcrun", "simctl", "bootstatus", udid, "-b", timeout=300)
    command("xcrun", "simctl", "install", udid, app, timeout=180)


def run(alternatives=None, port=None):
    udid = (WORK / "device").read_text()
    from probe import channels
    selection = {} if alternatives is not None else channels()
    env = dict(os.environ)
    env["SIMCTL_CHILD_TEST_PASSWORD"] = env.pop("DIEGUITO2026_PASSWORD")
    if alternatives is not None:
        env["SIMCTL_CHILD_VARIANT_IDS"] = ",".join(alternatives)
        if port:
            env["SIMCTL_CHILD_SAMPLE_PORT"] = str(port)
    env["SIMCTL_CHILD_PRIMARY_ID"] = selection.get("gol peru l", "")
    env["SIMCTL_CHILD_LIGA_ID"] = selection.get("liga 1 max", "")
    env["SIMCTL_CHILD_CONTROL_ID"] = selection.get("canal n", "")
    container = Path(command("xcrun", "simctl", "get_app_container", udid, BUNDLE, "data"))
    output = container / "Documents" / "verification.jsonl"
    output.unlink(missing_ok=True)
    command("xcrun", "simctl", "launch", udid, BUNDLE, env=env, timeout=75)
    observed = 0
    try:
        deadline = time.monotonic() + (30 * (len(alternatives) + 3) + 30 if alternatives is not None else 225)
        while time.monotonic() < deadline:
            if output.exists():
                rows = output.read_text().splitlines()
                for line in rows[observed:]:
                    try:
                        result = json.loads(line)
                    except ValueError:
                        break
                    print(json.dumps(result), flush=True)
                    observed += 1
                    if result["stage"] == "finished":
                        return
            time.sleep(1)
        raise RuntimeError("missing_simulator_completion")
    finally:
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        prepare()
    else:
        try:
            run()
        except Exception as error:
            # Raw network and simulator diagnostic objects can contain URLs.
            print("Simulator check stopped:", type(error).__name__, flush=True)
            raise SystemExit(1)
