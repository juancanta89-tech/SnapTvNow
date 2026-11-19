"""Count complete origin TS segments from the named channel without retaining media."""
import re
import time
from probe import channels, emit, media_url, own_url, open_url, ProbeError

def playlist(url):
    with open_url(url) as response:
        if response.headers.get("X-SMN-Live-Fallback") == "next-channel":
            raise ProbeError("unexpected_fallback")
        body = response.read(262145)
        if len(body) > 262144:
            raise ProbeError("playlist_limit")
        return body.decode("utf-8-sig"), response.geturl()

def measure():
    identifier = channels().get("gol peru l")
    if identifier != "876":
        raise ProbeError("catalog_changed")
    seen = set()
    source = media_url(identifier, "m3u8")
    root, address = playlist(source)
    if "#EXT-X-STREAM-INF" in root:
        first = next(line.strip() for line in root.splitlines() if line.strip() and not line.startswith("#"))
        address = own_url(first,address)
    for wave in range(3):
        body, base = playlist(address)
        if "#EXT-X-BYTERANGE" in body or "#EXT-X-MAP" in body:
            raise ProbeError("source_is_not_whole_ts_segments")
        seqmatch = re.search(r"^#EXT-X-MEDIA-SEQUENCE:(\d+)",body,re.M)
        if not seqmatch:
            raise ProbeError("missing_media_sequence")
        first_sequence = int(seqmatch[1])
        rows = []
        duration = None
        for raw in body.splitlines():
            line = raw.strip()
            if line.startswith("#EXTINF:"):
                duration = float(line[8:].split(",",1)[0])
            if line and not line.startswith("#"):
                rows.append((first_sequence+len(rows),duration,own_url(line,base)))
        for seq,duration,url in rows[-5:]:
            if seq in seen:
                continue
            try:
                with open_url(url) as response:
                    length = response.headers.get("Content-Length","")
                    count = 0
                    confirmed_ts = None
                    while count <= 24*1024*1024:
                        chunk = response.read(131072)
                        if not chunk:
                            break
                        if confirmed_ts is None:
                            confirmed_ts = any(chunk[i:i+1]==b'\x47' and chunk[i+188:i+189]==b'\x47'
                                               and chunk[i+376:i+377]==b'\x47' for i in range(min(188,len(chunk)-376)))
                        count += len(chunk)
                    complete = count <= 24*1024*1024 and (not length.isdigit() or count == int(length))
                    emit(stage="segment_size", public_id=identifier, sequence=seq, duration=duration,
                         bytes=count, header_bytes=int(length) if length.isdigit() else None, complete=complete,
                         confirmed_ts=confirmed_ts, status=response.status)
                    if complete:
                        seen.add(seq)
            except Exception as exc:
                emit(stage="segment_measure_error", sequence=seq,error=str(exc) if isinstance(exc,ProbeError) else type(exc).__name__)
        if len(seen) >= 8 or wave == 2:
            break
        time.sleep(min(25,max(5,int((rows[-1][1] or 5)*2+1))) if rows else 10)
    emit(stage="segment_measure_complete", count=len(seen))
if __name__ == "__main__":
    try:
        measure()
    except Exception as exc:
        emit(stage="segment_setup", error=str(exc) if isinstance(exc,ProbeError) else type(exc).__name__)
        raise SystemExit(1)
