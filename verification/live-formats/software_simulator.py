"""Install the pinned VideoLAN engine in the isolated diagnostic iPhone app."""
import os
import platform
import plistlib
from pathlib import Path
import shutil
import subprocess
import sys

from simulator import HERE, command, prepare_bundle, run


def install():
    sdk, app, udid = prepare_bundle()
    xc = next(Path(os.environ["RUNNER_TEMP"]).joinpath("vlckit").rglob("MobileVLCKit.xcframework"))
    with (xc / "Info.plist").open("rb") as fp:
        specs = plistlib.load(fp)["AvailableLibraries"]
    arch = platform.machine()
    selected = next(row for row in specs if row.get("SupportedPlatformVariant") == "simulator"
                    and row.get("SupportedPlatform") == "ios" and arch in row["SupportedArchitectures"])
    provider = xc / selected["LibraryIdentifier"]
    frameworks = ("UIKit Foundation QuartzCore CoreText AVFoundation Security CFNetwork AudioToolbox "
                  "OpenGLES CoreGraphics VideoToolbox CoreMedia CoreVideo CoreAudio Accelerate Metal").split()
    args = ["xcrun", "--sdk", "iphonesimulator", "clang", "-fobjc-arc", "-ObjC", "-isysroot", sdk,
            "-target", arch+"-apple-ios17.0-simulator", "-F", str(provider), str(HERE / "SoftwarePlayback.m")]
    for framework in frameworks + ["MobileVLCKit"]:
        args.extend(["-framework", framework])
    args += ["-lc++", "-lxml2", "-lz", "-lbz2", "-liconv", "-Wl,-rpath,@executable_path/Frameworks",
             "-o", str(app/"LiveFormats")]
    subprocess.run(args, check=True, timeout=150)
    fw = provider / selected["LibraryPath"]
    if "dynamically linked shared library" in command("file", fw / "MobileVLCKit"):
        (app / "Frameworks").mkdir(exist_ok=True)
        dest = app / "Frameworks" / fw.name
        shutil.copytree(fw, dest, symlinks=True)
        command("codesign", "--force", "--sign", "-", dest)
    command("codesign", "--force", "--sign", "-", app)
    command("xcrun", "simctl", "install", udid, app, timeout=90)


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        install()
    else:
        try:
            run()
        except Exception as err:
            print("Software check stopped:", type(err).__name__, flush=True)
            raise SystemExit(1)
