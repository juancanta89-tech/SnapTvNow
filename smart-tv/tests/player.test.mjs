import test from 'node:test';
import assert from 'node:assert/strict';
import {Player} from '../src/player.js';
test('Samsung uses AVPlay prepare/play, pause, seeking and close; ignores stale prepare',async()=>{
  const calls=[];let prepared;globalThis.window={SNAP_PLATFORM:'tizen',webapis:{avplay:{
    open:u=>calls.push(['open',u]),setDisplayRect:()=>{},setDisplayMethod:()=>{},setListener:()=>{},prepareAsync:cb=>{prepared=cb;},
    play:()=>calls.push(['play']),pause:()=>calls.push(['pause']),stop:()=>calls.push(['stop']),close:()=>calls.push(['close']),getDuration:()=>60000,getCurrentTime:()=>5000,seekTo:n=>calls.push(['seek',n])}}};
  const p=new Player({},()=>{});await p.open({url:'https://example.test/demo.mp4',section:'Películas'});prepared();p.toggle('pause');p.seek(10);p.stop();
  assert.ok(calls.some(c=>c[0]==='play'));assert.ok(calls.some(c=>c[0]==='pause'));assert.ok(calls.some(c=>c[0]==='seek'&&c[1]===15000));
  const n=calls.length;prepared();assert.equal(calls.length,n);
});
test('webOS uses native HTML video and blocks live seek',async()=>{
  globalThis.window={SNAP_PLATFORM:'webos'};const calls=[];
  const video={duration:60,currentTime:5,play:async()=>calls.push('play'),pause:()=>calls.push('pause'),load:()=>{},removeAttribute:()=>{}};
  const p=new Player(video,()=>{});await p.open({url:'https://example.test/test.mp4',section:'TV en vivo'});p.seek(10);assert.equal(video.currentTime,5);p.toggle('pause');p.stop();assert.ok(calls.includes('play'));assert.ok(calls.includes('pause'));
});
