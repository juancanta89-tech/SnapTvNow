"""Private bounded source acquisition, followed by local-only Apple playback."""
import contextlib
import functools
import http.server
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "live-formats"))
from simulator import BUNDLE, WORK, command, prepare_bundle
from probe import open_url, own_url, media_url, ProbeError

def prepare():
    sdk, app, device = prepare_bundle(wait_for_boot=False)
    args = ["xcrun","--sdk","iphonesimulator","clang","-fobjc-arc","-fmodules","-isysroot",sdk,
            "-target",platform.machine()+"-apple-ios17.0-simulator",str(HERE/"Continuity.m")]
    for fw in ["Foundation","UIKit","AVFoundation","CoreMedia","CoreVideo","QuartzCore"]:
        args.extend(["-framework",fw])
    command(*args,"-o",app/"LiveFormats",timeout=300)
    command("codesign","--force","--sign","-",app)
    command("xcrun","simctl","bootstatus",device,"-b",timeout=480)
    command("xcrun","simctl","install",device,app,timeout=180)

def fetch(url, limit):
    with open_url(url) as response:
        if response.headers.get("X-SMN-Live-Fallback")=="next-channel":
            raise ProbeError("other_channel")
        payload=response.read(limit+1)
        if len(payload)>limit:
            raise ProbeError("resource_limit")
        if response.headers.get("Content-Length") and len(payload)!=int(response.headers["Content-Length"]):
            raise ProbeError("incomplete_resource")
        return payload, own_url(response.geturl())

def collect(root):
    dest=root/"src-real"
    dest.mkdir()
    current=media_url("876","m3u8")
    for _ in range(3):
        data,base=fetch(current,256*1024); text=data.decode("utf-8-sig")
        if "#EXT-X-STREAM-INF:" not in text:
            break
        current=own_url(next(l.strip() for l in text.splitlines() if l.strip() and not l.startswith("#")),base)
    else:
        raise ProbeError("playlist_depth")
    parts=[]; total=0; last=None
    deadline=time.monotonic()+105
    seek=True
    while time.monotonic()<deadline:
        if not text.startswith("#EXTM3U") or any(t in text for t in ["#EXT-X-BYTERANGE","#EXT-X-MAP:","#EXT-X-KEY:"]):
            raise ProbeError("unexpected_source_playlist")
        seq=re.search(r'^#EXT-X-MEDIA-SEQUENCE:(\d+)',text,re.M)
        number=int(seq[1]) if seq else 0
        rows=[]; duration=None; boundary=False
        for line in text.splitlines():
            if line.startswith("#EXT-X-DISCONTINUITY") and ":" not in line: boundary=True
            if line.startswith("#EXTINF:"): duration=float(line.partition(":")[2].split(",")[0])
            if line and not line.startswith("#") and duration:
                rows.append((number,duration,own_url(line,base),boundary));number+=1;duration=None;boundary=False
        if seek: rows=rows[-4:];seek=False
        for seq,duration,url,boundary in rows:
            if last is not None and seq<=last: continue
            if last is not None and (seq!=last+1 or boundary): parts=[];total=0
            try: payload,_=fetch(url,12*1024*1024)
            except ProbeError as error:
                if str(error) in ("http_404","http_410"):break
                raise
            if len(payload)<188*3 or payload[0]!=0x47: raise ProbeError("invalid_transport")
            name=f"segment-{seq}.ts";(dest/name).write_bytes(payload)
            parts.append((seq,duration,name,len(payload)));total+=duration;last=seq
            if len(parts)>=3 and total>=25: break
        if len(parts)>=3 and total>=25:break
        time.sleep(1.5)
        data,base=fetch(current,256*1024);text=data.decode("utf-8-sig")
    if len(parts)<3 or total<25:raise ProbeError("short_live_window")
    target=max(1,int(max(p[1] for p in parts)+.999))
    lines=["#EXTM3U","#EXT-X-VERSION:3",f"#EXT-X-TARGETDURATION:{target}",f"#EXT-X-MEDIA-SEQUENCE:{parts[0][0]}","#EXT-X-PLAYLIST-TYPE:VOD"]
    for _,duration,name,_ in parts:lines.extend([f"#EXTINF:{duration},",name])
    (dest/"index.m3u8").write_text("\n".join(lines+["#EXT-X-ENDLIST",""]))
    metadata=json.loads(subprocess.check_output(["ffprobe","-v","error","-show_entries","stream=codec_type,codec_name,width,height,field_order","-of","json",str(dest/parts[0][2])],stderr=subprocess.DEVNULL,timeout=25))["streams"]
    print(json.dumps(dict(case="real",public_id=876,segments=len(parts),seconds=round(total,3),sizes=[p[3] for p in parts],source_tracks=metadata)),flush=True)
    if not any(x.get("codec_name")=="h264" for x in metadata) or not any(x.get("codec_name")=="mp2" for x in metadata):
        raise ProbeError("original_codec_changed")
    return dest

def synthetic(root):
    d=root/"src-synthetic";d.mkdir()
    subprocess.run(["ffmpeg","-loglevel","error","-f","lavfi","-i","color=c=0x254080:s=1920x1080:r=60000/1001",
       "-f","lavfi","-i","sine=frequency=997:sample_rate=48000","-t","29","-vf","tinterlace=mode=interleave_top",
       "-pix_fmt","yuv420p","-flags","+ilme+ildct","-c:v","libx264","-threads","3","-preset","veryfast","-crf","30",
       "-x264-params","tff=1:keyint=120:min-keyint=120:scenecut=0","-c:a","mp2","-b:a","128k","-ac","2","-ar","48000",
       "-f","hls","-hls_time","4","-hls_playlist_type","vod","-hls_segment_filename",str(d/"part-%02d.ts"),str(d/"index.m3u8")],check=True,timeout=90)
    return d

class Quiet(http.server.SimpleHTTPRequestHandler):
    extensions_map={".m3u8":"application/vnd.apple.mpegurl",".mp4":"video/mp4",".m4s":"video/iso.segment"}
    def log_message(self,*args):pass

def certify_audio(out):
    result=subprocess.run(["ffmpeg","-v","error","-protocol_whitelist","file,pipe,crypto","-i",str(out/"index.m3u8"),
        "-map","0:a:0","-t","24","-ar","8000","-ac","1","-f","s16le","pipe:1"],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=45,check=True)
    import array
    source=array.array("h");source.frombytes(result.stdout)
    windows=[max(abs(v) for v in source[i:i+32000]) for i in range(0,len(source)-31999,32000)]
    print(json.dumps(dict(case=out.name,decoded_pcm_seconds=len(source)/8000,non_silent_four_second_windows=sum(v>4 for v in windows))),flush=True)
    return len(source)>=160000 and len(windows)>=5 and sum(v>4 for v in windows)>=3

def measure(name,port):
    dev=(WORK/"device").read_text();data=Path(command("xcrun","simctl","get_app_container",dev,BUNDLE,"data")); report=data/"Documents"/"continuity.json"
    report.unlink(missing_ok=True)
    env=dict(os.environ);env.pop("DIEGUITO2026_PASSWORD",None)
    env.update(SIMCTL_CHILD_PORT=str(port),SIMCTL_CHILD_SAMPLE=name)
    command("xcrun","simctl","launch",dev,BUNDLE,env=env,timeout=90)
    try:
        until=time.monotonic()+75
        while time.monotonic()<until:
            if report.is_file():
                result=json.loads(report.read_text()); print(json.dumps(result),flush=True);return result["ok"]
            time.sleep(.5)
        return False
    finally:
        subprocess.run(["xcrun","simctl","terminate",dev,BUNDLE],capture_output=True)

def run():
    with tempfile.TemporaryDirectory() as tmp:
        root=Path(tmp); public=root/"served";public.mkdir()
        server=http.server.ThreadingHTTPServer(("127.0.0.1",0),functools.partial(Quiet,directory=str(public)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        success=True
        try:
            for name in ("real","synthetic"):
                try:
                    src=WORK/"continuity-source"/"src-real" if name=="real" else synthetic(root)
                    output=public/name
                    subprocess.run(["node",str(Path(os.environ["APPLE_EDGE_REPO"])/"cloudflare-worker/tests/apple-live-fixture.mjs"),str(src/"index.m3u8"),str(output)],check=True,timeout=180)
                    audio=certify_audio(output);video=measure(name,server.server_port);success=success and audio and video
                except Exception as exc:
                    print(json.dumps(dict(case=name,error=str(exc) if isinstance(exc,ProbeError) else type(exc).__name__)),flush=True);success=False
        finally:server.shutdown();server.server_close()
        return success

if __name__=="__main__":
    if sys.argv[1]=="prepare":prepare()
    elif sys.argv[1]=="capture":
        try:
            dest=WORK/"continuity-source"
            dest.mkdir(exist_ok=True)
            for attempt in range(3):
                with tempfile.TemporaryDirectory(dir=dest) as temporary:
                    try:
                        result=collect(Path(temporary))
                        result.rename(dest/"src-real")
                        break
                    except ProbeError as error:
                        retry=str(error) in {"http_404","http_410","http_429","http_502","http_503","http_504","connection_error","short_live_window"}
                        if not retry or attempt == 2:raise
                        print(json.dumps(dict(stage="source_retry",attempt=attempt+1,reason=str(error))),flush=True)
                        time.sleep(2+2*attempt)
        except Exception as exc:
            print(json.dumps(dict(stage="capture",error=str(exc) if isinstance(exc,ProbeError) else type(exc).__name__)),flush=True)
            sys.exit(1)
    else:sys.exit(0 if run() else 1)
