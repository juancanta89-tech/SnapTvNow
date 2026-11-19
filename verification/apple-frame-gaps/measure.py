"""Compare source frame timing with iPhone presentation from the exact same capture."""
import functools
import http.server
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading
import run

def timeline(playlist):
    cmd=["ffprobe","-v","error","-protocol_whitelist","file,pipe,crypto","-select_streams","v:0","-show_packets","-show_entries","packet=pts_time,flags,duration_time","-of","json",str(playlist)]
    packets=json.loads(subprocess.check_output(cmd,stderr=subprocess.DEVNULL,timeout=95))["packets"]
    pts=sorted((float(p["pts_time"]),float(p["duration_time"]) if p.get("duration_time") not in (None,"N/A") else .033367,p.get("flags","")) for p in packets if p.get("pts_time") not in (None,"N/A"))
    anchor=pts[0][0]
    gaps=[(round(a[0]-anchor,3),round(b[0]-anchor,3)) for a,b in zip(pts,pts[1:]) if b[0]-a[0]>.5]
    keys=[round(t-anchor,3) for t,d,f in pts if "K" in f]
    end=round(pts[-1][0]-anchor+pts[-1][1],3)
    return dict(frames=len(pts),seconds=end,gaps=gaps,keyframes=keys)

def clean_start(source):
    start=0
    for a,b in source["gaps"]+[(source["seconds"],source["seconds"])]:
        valid=[k for k in source["keyframes"] if k>=start and k+21.5<=a]
        if valid:return round(valid[0]+.15,3)
        start=b+.3
    return None

def main():
    src=run.WORK/"continuity-source"/"src-real"/"index.m3u8"
    with tempfile.TemporaryDirectory() as tmp:
        root=Path(tmp);out=root/"real"
        fixture=Path(os.environ["APPLE_EDGE_REPO"])/"cloudflare-worker/tests/apple-live-fixture.mjs"
        subprocess.run(["node",str(fixture),str(src),str(out)],check=True,timeout=180)
        source=timeline(src);rendered=timeline(out/"index.m3u8")
        print(json.dumps(dict(case="exact_timing",source=source,packaged=rendered)),flush=True)
        clean=clean_start(source)
        for name in ["real-clean","real-damaged"]:(root/name).symlink_to(out,target_is_directory=True)
        server=http.server.ThreadingHTTPServer(("127.0.0.1",0),functools.partial(run.Quiet,directory=str(root)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        try:
            audio=run.certify_audio(out)
            baseline=run.measure("real",server.server_port) or {}
            if clean is None:
                print(json.dumps(dict(case="real-clean",error="no_continuous_source_window")),flush=True)
                clean_result=None
            else:
                print(json.dumps(dict(case="real-clean",seek_seconds=clean)),flush=True)
                clean_result=run.measure("real-clean",server.server_port,clean)
            damaged_start=max(0,round(source["gaps"][0][0]-5,3)) if source["gaps"] else None
            if damaged_start is not None:
                if damaged_start>10 or (not baseline.get("gaps_in_item_ms")):
                    print(json.dumps(dict(case="real-damaged",seek_seconds=damaged_start)),flush=True)
                    damaged=run.measure("real-damaged",server.server_port,damaged_start)
                else:damaged=baseline;damaged_start=0
                ref=root/"real-copy";ref.mkdir()
                args=["ffmpeg","-v","error","-protocol_whitelist","file,pipe,crypto","-i",str(src),"-map","0:v:0","-map","0:a:0","-c:v","copy","-af","aresample=async=1","-c:a","aac","-b:a","128k","-f","hls","-hls_segment_type","fmp4","-hls_time","4","-hls_playlist_type","vod","-hls_segment_filename",str(ref/"part-%03d.m4s"),str(ref/"index.m3u8")]
                subprocess.run(args,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True,timeout=150)
                print(json.dumps(dict(case="real-copy",seek_seconds=damaged_start,clock=timeline(ref/"index.m3u8"))),flush=True)
                run.measure("real-copy",server.server_port,damaged_start)
            return bool(audio and clean_result and clean_result.get("ok") and baseline.get("rendered_frames",0)>0)
        finally:server.shutdown();server.server_close()
if __name__=="__main__":sys.exit(0 if main() else 1)
