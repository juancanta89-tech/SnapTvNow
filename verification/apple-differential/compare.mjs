import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {convertWithModule} from '../apple-continuity/edge/cloudflare-worker/src/codec-runtime.mjs';
import {convertWithModule as convertPrior} from './prior.mjs';
const tmp=process.env.RUNNER_TEMP;
const original=await WebAssembly.compile(await readFile(join(tmp,'original.wasm')));
const candidate=await WebAssembly.compile(await readFile(join(tmp,'candidate.wasm')));
const seen=new Set();
let fault=0,recovered=0,errors=0;
for(const round of [0,1,2]){
  const sample=join(tmp,'round-'+round);
  const list=(await readFile(join(sample,'index.m3u8'),'utf8')).split('\n').filter(l=>l && !l.startsWith('#'));
  for(const name of list){
    const bytes=await readFile(join(sample,name));
    const digest=createHash('sha256').update(bytes).digest('hex');
    if(seen.has(digest))continue;
    seen.add(digest);
    const sequence=seen.size;
    const attempt=(module,convert)=>{try{
      const out=convert(module,bytes,{sequence,epochMs:Date.now()});
      return {ok:out.init.length>0 && out.media.length>0,diagnostics:out.diagnostics};
    }catch(e){return {ok:false,code:e.message.match(/^apple_segment_codec_\d+_-?\d+$/)?.[0] || 'error',diagnostics:e.diagnostics}}};
    const was=attempt(original,convertPrior),now=attempt(candidate,convertWithModule);
    if(!was.ok)fault++;
    if(!now.ok)errors++;
    if(!was.ok&&now.ok)recovered++;
    console.log(JSON.stringify({segment:sequence,bytes:bytes.length,original:was,candidate:now}));
  }
}
console.log(JSON.stringify({case:'same_bytes',segments:seen.size,fault,recovered,errors}));
if(errors)process.exitCode=1;
