"""Verify native-facing production playlists with the authorized control account. Emit metrics only."""
import math
import re
import struct
import subprocess
import tempfile
from array import array
from pathlib import Path
import probe
from probe import own_url, emit, ProbeError

probe.UA = "AppleCoreMedia/1.0.0.22G86 (iPhone; U; CPU OS 18_6 like Mac OS X; en_us)"
LIMIT = 12*1024*1024

def fetch(url, budget):
    with probe.open_url(url) as r:
        if r.headers.get("X-SMN-Live-Fallback")=="next-channel":
            raise ProbeError("other_channel_fallback")
        data = r.read(budget+1)
        if len(data)>budget or (r.headers.get("Content-Length","").isdigit()
                and len(data)!=int(r.headers["Content-Length"])):
            raise ProbeError("incomplete_or_oversize")
        return data, own_url(r.geturl())

def boxes(data):
    offset=0
    names=set()
    while offset+8<=len(data):
        size,tag=struct.unpack_from(">I4s",data,offset)
        skip=8
        if size==1:
            if offset+16>len(data):
                break
            size=struct.unpack_from(">Q",data,offset+8)[0]
            skip=16
        elif size==0:
            size=len(data)-offset
        if size<skip or offset+size>len(data):
            raise ProbeError("invalid_mp4_box")
        names.add(tag)
        offset+=size
    return names

def check(identifier):
    address=probe.media_url(identifier,"ts")
    for level in range(4):
        data, at = fetch(address,524288)
        body=data.decode("utf8")
        if not body.lstrip().startswith("#EXTM3U"):
            raise ProbeError("not_a_playlist")
        if "#EXT-X-STREAM-INF" not in body:
            break
        first=next(l.strip() for l in body.splitlines() if l.strip() and not l.strip().startswith("#"))
        address=own_url(first,at)
    else:
        raise ProbeError("playlist_depth")
    rows=[]; current_map=None; duration=None
    for line in body.splitlines():
        line=line.strip()
        if line.startswith("#EXT-X-MAP:"):
            match=re.search(r'URI="([^"]+)"',line)
            current_map=own_url(match.group(1),at) if match else None
        elif line.startswith("#EXTINF:"):
            duration=float(line[8:].split(",",1)[0])
        elif line and not line.startswith("#"):
            rows.append((current_map,own_url(line,at),duration))
    emit(stage="release_playlist",public_id=identifier,map_present=any(x[0] for x in rows),
         master_levels=level,entries=len(rows))
    if len(rows)<2 or any(not r[0] for r in rows[-2:]):
        raise ProbeError("missing_fragmented_map")
    # Prefer slightly behind the moving tip where possible.
    selected=rows[-3:-1] if len(rows)>=3 else rows[-2:]
    cached={}
    status=True
    for index,(init,url,duration) in enumerate(selected):
        if init not in cached:
            cached[init],_=fetch(init,1024*1024)
        initialization=cached[init]
        segment,_=fetch(url,LIMIT)
        init_ok={b"ftyp",b"moov"}<=boxes(initialization)
        media_ok={b"moof",b"mdat"}<=boxes(segment)
        if not init_ok or not media_ok:
            raise ProbeError("invalid_fragmented_mp4")
        joined=initialization+segment
        decoded=probe.decode(joined)
        with tempfile.TemporaryDirectory() as root:
            src=Path(root)/"temporary.mp4";src.write_bytes(joined)
            pcm=subprocess.run(["ffmpeg","-nostdin","-v","error","-i",str(src),"-map","0:a:0","-t","3","-ac","1","-ar","8000","-f","s16le","pipe:1"],capture_output=True,timeout=25).stdout
            values=array('h');values.frombytes(pcm)
            rms=round(math.sqrt(sum(x*x for x in values)/len(values)),1) if values else None
        track_types={t.get("codec_name") for t in decoded["tracks"]}
        valid={"aac","h264"}<=track_types and decoded["video_frames"]>0 and decoded["audio_frames"]>0 and bool(values)
        emit(stage="release_fragment",public_id=identifier,index=index,duration_seconds=duration,init_bytes=len(initialization),
             fragment_bytes=len(segment),valid_boxes=init_ok and media_ok,video_frames=decoded["video_frames"],
             audio_frames=decoded["audio_frames"],audio_pcm_samples=len(values),audio_rms=rms,
             h264="h264" in track_types,aac="aac" in track_types,passed=valid)
        status=status and valid
    return status

def main():
    good=True
    for identifier in ("876","877","878"):
        try:
            good=check(identifier) and good
        except Exception as ex:
            emit(stage="release_failure",public_id=identifier,error=str(ex) if isinstance(ex,ProbeError) else type(ex).__name__)
            good=False
    return good

if __name__=="__main__":
    raise SystemExit(0 if main() else 1)
