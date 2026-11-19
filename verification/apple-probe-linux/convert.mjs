import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {convertWithModule} from '../apple-continuity/edge/cloudflare-worker/src/codec-runtime.mjs';
const bin=resolve(process.env.APPLE_EDGE_REPO,'cloudflare-worker/src/apple-segment-codec.wasm');
const module=await WebAssembly.compile(await readFile(bin));
const root=resolve(process.env.RUNNER_TEMP,'live-format-simulator/continuity-source/src-real');
const names=(await readFile(resolve(root,'index.m3u8'),'utf8')).split('\n').filter(n=>n && !n.startsWith('#'));
let count=0;
for(const [i,name] of names.entries()){
  try {
    const result=convertWithModule(module,await readFile(resolve(root,name)),{sequence:i,epochMs:Date.now()});
    console.log(JSON.stringify({segment:i,success:true,media:result.media.length,init:result.init.length}));
    count++;
  }catch(error){const message=error.message.match(/^apple_segment_codec_\d+_-?\d+$/)?.[0]||'other';
    console.log(JSON.stringify({segment:i,success:false,diag:message}));
  }
}
process.exitCode=(count===names.length ? 0 : 1);
