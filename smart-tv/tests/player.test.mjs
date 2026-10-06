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
test('VOD restores position, saves on close and clears completed playback',async()=>{
  globalThis.window={SNAP_PLATFORM:'webos'};let saved=0,cleared=false;
  const video={duration:120,currentTime:0,play:async()=>{},pause:()=>{},load:()=>{},removeAttribute:()=>{}};
  const p=new Player(video,()=>{},{read:()=>30,write:(_,n)=>saved=n,clear:()=>cleared=true});
  await p.open({id:'movie1',url:'https://example.test/test.mp4',section:'Películas'});
  video.onloadedmetadata();assert.equal(video.currentTime,30);
  video.currentTime=45;p.stop();assert.equal(saved,45);
  await p.open({id:'movie1',url:'https://example.test/test.mp4',section:'Películas'});
  video.onended();p.stop();assert.equal(cleared,true);assert.equal(saved,45);
});
test('HTML track selection enables one audio and turns subtitles off',()=>{
  globalThis.window={SNAP_PLATFORM:'webos'};
  const video={audioTracks:[{language:'es',enabled:true},{language:'en',enabled:false}],textTracks:[{language:'es',mode:'showing'},{language:'en',mode:'disabled'}]};
  const p=new Player(video,()=>{});assert.equal(p.tracks('AUDIO')[0].label,'es');
  p.selectTrack('AUDIO',1);assert.deepEqual(video.audioTracks.map(t=>t.enabled),[false,true]);
  p.selectTrack('TEXT',-1);assert.ok(video.textTracks.every(t=>t.mode==='disabled'));
});
