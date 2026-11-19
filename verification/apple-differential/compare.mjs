import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {existsSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {convertWithModule} from '../apple-continuity/edge/cloudflare-worker/src/codec-runtime.mjs';
import {convertWithModule as convertPrior} from './prior.mjs';
const tmp=process.env.RUNNER_TEMP;
const original=await WebAssembly.compile(await readFile(join(tmp,'original.wasm')));
const candidate=await WebAssembly.compile(await readFile(join(tmp,'candidate.wasm')));
const mediaFile=join(tmp,'current-conversion.mp4');
function timeline(path,kind){
  const raw=execFileSync('ffprobe',['-v','error','-select_streams',kind+':0','-show_packets','-show_entries','packet=pts_time,duration_time','-of','json',path],{stdio:['ignore','pipe','ignore'],encoding:'utf8'});
  const points=JSON.parse(raw).packets.map(p=>({t:Number(p.pts_time),d:Number(p.duration_time)||0})).filter(p=>Number.isFinite(p.t)).sort((a,b)=>a.t-b.t);
  if(!points.length)throw Error('no_packets');
  const listed=points.map(p=>p.d).filter(d=>d>0).sort((a,b)=>a-b);
  const nominal=listed[Math.floor(listed.length/2)] || (kind==='a'?1024/48000:1001/30000);
  const duration=(p)=>p.d||nominal;
  const start=points[0].t,end=Math.max(...points.map(p=>p.t+duration(p)));
  const gap=points.slice(1).reduce((m,p,i)=>Math.max(m,p.t-points[i].t-duration(points[i])),0);
  return {start,end,gap};
}
function compareTiming(source){
  const modulo=(s)=>s-Math.round(s/(2**33/90000))*(2**33/90000);
  const ms=(s)=>Math.round(s*1000);
  const results={};
  for(const kind of ['a','v']){
    const incoming=timeline(source,kind), packaged=timeline(mediaFile,kind);
    results[kind]={start_diff_ms:ms(modulo(packaged.start-incoming.start)),end_diff_ms:ms(modulo(packaged.end-incoming.end)),source_span_ms:ms(incoming.end-incoming.start),output_span_ms:ms(packaged.end-packaged.start),source_max_gap_ms:ms(incoming.gap),output_max_gap_ms:ms(packaged.gap)};
  }
  return results;
}
const seen=new Set();
let fault=0,recovered=0,errors=0;
for(const round of [0,1,2]){
  const sample=join(tmp,'round-'+round);
  if(!existsSync(sample))continue;
  const list=(await readFile(join(sample,'index.m3u8'),'utf8')).split('\n').filter(l=>l && !l.startsWith('#'));
  for(const name of list){
    const bytes=await readFile(join(sample,name));
    const digest=createHash('sha256').update(bytes).digest('hex');
    if(seen.has(digest))continue;
    seen.add(digest);
    const sequence=seen.size;
    const attempt=(module,convert)=>{try{
      const out=convert(module,bytes,{sequence,epochMs:Date.now()});
      if(module===candidate)writeFileSync(mediaFile,Buffer.concat([out.init,out.media]));
      return {ok:out.init.length>0 && out.media.length>0,diagnostics:out.diagnostics};
    }catch(e){return {ok:false,code:e.message.match(/^apple_segment_codec_\d+_-?\d+$/)?.[0] || 'error',diagnostics:e.diagnostics}}};
    const was=attempt(original,convertPrior),now=attempt(candidate,convertWithModule);
    let timing;
    if(!was.ok || !now.ok){
      const raw=execFileSync('ffprobe',['-v','error','-select_streams','a:0','-show_packets','-show_entries','packet=pts_time,duration_time,size','-of','json',join(sample,name)],{stdio:['ignore','pipe','ignore'],encoding:'utf8'});
      const packets=JSON.parse(raw).packets;
      const first=Number(packets[0].pts_time), at=(now.diagnostics?.firstInvalid??1)-1;
      const gaps=packets.slice(1).map((p,i)=>Math.round((Number(p.pts_time)-Number(packets[i].pts_time)-Number(packets[i].duration_time??0.024))*1000));
      const axis=packets.map((p,i)=>({index:i+1,rel_ms:Math.round((Number(p.pts_time)-first)*1000),duration_ms:Math.round(Number(p.duration_time??0.024)*1000),bytes:Number(p.size),offset_vs_24ms:Math.round((Number(p.pts_time)-first-i*0.024)*1000)}));
      const pcm=execFileSync('ffmpeg',['-v','error','-i',join(sample,name),'-map','0:a:0','-ac','1','-ar','48000','-f','s16le','pipe:1'],{stdio:['ignore','pipe','ignore'],maxBuffer:16*1024*1024});
      timing={ffprobe_packets:packets.length,first_pts90:Math.round(first*90000),last_pts90:Math.round(Number(packets.at(-1).pts_time)*90000),decoded_samples_48k:pcm.length/2,unusual_gaps:gaps.filter(t=>Math.abs(t)>3).slice(0,12),near_bad:axis.slice(Math.max(0,at-2),at+4)};
    }
    if(!was.ok)fault++;
    if(!now.ok)errors++;
    if(!was.ok&&now.ok)recovered++;
    console.log(JSON.stringify({segment:sequence,bytes:bytes.length,original:was,candidate:now,timing,clock:now.ok?compareTiming(join(sample,name)):undefined}));
  }
}
console.log(JSON.stringify({case:'same_bytes',segments:seen.size,fault,recovered,errors}));
if(errors)process.exitCode=1;
