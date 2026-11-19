"""Emit aggregate decoder and MPEG header diagnostics. Inputs stay local."""
import array
import collections
import json
import os
from pathlib import Path
import re
import subprocess

root=Path(os.environ['RUNNER_TEMP'])/'live-format-simulator'/'continuity-source'/'src-real'
for n,name in enumerate(p for p in (root/'index.m3u8').read_text().splitlines() if p and not p.startswith('#')):
    path=root/name
    probe=subprocess.run(['ffprobe','-v','error','-select_streams','a:0','-show_packets','-show_data','-show_entries','packet=data,size,pts_time,flags','-of','json',str(path)],capture_output=True,timeout=35,check=True)
    packets=json.loads(probe.stdout)['packets']
    lost=[]
    for index,p in enumerate(packets):
        line=re.search(r'00000000:\s+([a-fA-F0-9]{4})\s+([a-fA-F0-9]{4})',p.get('data',''))
        bits=(int(line[1]+line[2],16) if line else 0)
        if (bits >> 21 != 0x7ff or (bits>>17)&3 != 2 or (bits>>12)&15 in (0,15) or (bits>>10)&3 == 3):
            lost.append(index)
    res=subprocess.run(['ffmpeg','-v','warning','-i',str(path),'-map','0:a:0','-ar','8000','-ac','1','-f','s16le','pipe:1'],capture_output=True,timeout=35)
    dec=res.stderr.decode(errors='replace').lower()
    pcm=array.array('h');pcm.frombytes(res.stdout)
    print(json.dumps(dict(segment=n,bytes=path.stat().st_size,packets=len(packets),bad_mp2_headers=len(lost),first_bad_ids=lost[:8],last_bad_ids=lost[-4:],native_pcm_seconds=len(pcm)/8000,peak=max((abs(i) for i in pcm),default=0),native_warnings={k:dec.count(k) for k in ('header missing','invalid frame','invalid data','packet corrupt','pes packet size mismatch','multiple frames','overread','frame sync','error while decoding') if k in dec})),flush=True)
