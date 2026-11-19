"""Track each phase of the same 878 channel through Apple and ordinary HLS, without logging URLs."""
import re
import time
import json
import urllib.error
import urllib.parse
import urllib.request
import probe
import release_fragments as release
from probe import own_url, emit, ProbeError

APPLE=probe.UA
NORMAL="SNAPTVNOW-LiveFormatCheck/1.0"
actor="apple"
def kind(url):
    path=urllib.parse.urlsplit(url).path
    if path.startswith("/live/"): return "xtream_"+path.rsplit(".",1)[-1]
    if path.endswith(".m3u8"): return "signed_playlist"
    if path.endswith(".mp4"): return "signed_init"
    if path.endswith(".m4s"): return "signed_fragment"
    if path.endswith(".ts"): return "signed_ts"
    return "signed_media"
class Redirect(probe.OwnRedirect):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        safe=own_url(newurl,req.full_url)
        emit(stage="redirect",account_path=actor,phase=kind(req.full_url),status=code,next_phase=kind(safe))
        return super().redirect_request(req,fp,code,msg,headers,safe)
opener=urllib.request.build_opener(Redirect())
def tagged(url,budget):
    req=urllib.request.Request(own_url(url),headers={"User-Agent":probe.UA,"Accept-Encoding":"identity"})
    try:
        with opener.open(req,timeout=24) as r:
            actual=own_url(r.geturl())
            buf=r.read(budget+1)
            emit(stage="hop",account_path=actor,phase=kind(actual),status=r.status,bytes=len(buf))
            if len(buf)>budget or (r.headers.get("Content-Length","").isdigit() and len(buf)!=int(r.headers["Content-Length"])):
                raise ProbeError("incomplete_or_oversize")
            return buf,actual
    except urllib.error.HTTPError as exc:
        with exc:
            def safefield(value):
                return value if re.fullmatch(r"[a-zA-Z0-9_.-]{1,85}",str(value or "")) else None
            data=exc.read(2048)
            try: msg=json.loads(data).get("error")
            except Exception: msg=None
            emit(stage="http_failure",account_path=actor,phase=kind(exc.geturl()),status=exc.code,
                 edge_code=safefield(exc.headers.get("X-SMN-Error-Code")),json_code=safefield(msg),
                 request_id=safefield(exc.headers.get("cf-ray")))
            raise ProbeError("http_"+str(exc.code)) from None
def run():
    global actor
    release.fetch=tagged
    success=False
    for attempt in range(1,3):
        try:
            emit(stage="retry",attempt=attempt)
            success=release.check("878")
            if success: break
        except Exception as ex:
            emit(stage="apple_outcome",error=str(ex) if isinstance(ex,ProbeError) else type(ex).__name__)
        if attempt==1: time.sleep(4)
    time.sleep(3)
    actor="generic";probe.UA=NORMAL
    # Same public ID, same HLS entry, inspect one complete original media segment.
    try:
        url=probe.media_url("878","m3u8")
        for _ in range(4):
            body,base=tagged(url,524288)
            playlist=body.decode("utf8");items=probe.children(playlist,base)
            if not items: raise ProbeError("no_items")
            if "#EXT-X-STREAM-INF" not in playlist: break
            url=items[0]
        seg,_=tagged(items[-2] if len(items)>1 else items[-1],12*1024*1024)
        decoded=probe.decode(seg)
        emit(stage="generic_outcome",public_id="878",video_frames=decoded["video_frames"],audio_frames=decoded["audio_frames"],
             codecs=[t.get("codec_name") for t in decoded["tracks"]])
    except Exception as ex:
        emit(stage="generic_outcome",error=str(ex) if isinstance(ex,ProbeError) else type(ex).__name__)
    return success
if __name__=="__main__":
    raise SystemExit(0 if run() else 1)
