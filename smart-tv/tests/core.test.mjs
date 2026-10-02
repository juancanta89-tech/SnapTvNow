import test from 'node:test';
import assert from 'node:assert/strict';
import {demoCatalog,nextGridIndex,selectItems,serverOrigin,Store,ppvCategory} from '../src/core.js';
import {Panel} from '../src/panel.js';
test('five-column navigation respects rows and incomplete final row',()=>{
  assert.equal(nextGridIndex(4,'ArrowRight',12),4);assert.equal(nextGridIndex(5,'ArrowLeft',12),5);
  assert.equal(nextGridIndex(6,'ArrowDown',12),11);assert.equal(nextGridIndex(8,'ArrowDown',12),8);
  assert.equal(nextGridIndex(3,'ArrowUp',12),-2);assert.equal(nextGridIndex(11,'ArrowUp',12),6);
});
test('demo catalog supports search, favorites and isolated test sections',()=>{
  const items=demoCatalog();assert.equal(selectItems(items,'TV en vivo','',[]).length,15);
  assert.equal(selectItems(items,'Buscar','Película',[]).length,5);
  assert.equal(selectItems(items,'Favoritos','',[items[0].id]).length,1);
  assert.ok(items.every(i=>i.id.startsWith('demo-')));assert.equal(items.filter(i=>i.seriesId).length,5);
});
test('origins reject insecure, credential-bearing and malformed panel addresses',()=>{
  assert.equal(serverOrigin('https://example.org/'),'https://example.org');
  for(const raw of ['http://example.org','https://u:p@example.org','https://example.org/path','https://example.org?q=1','javascript:alert(1)'])assert.throws(()=>serverOrigin(raw));
});
test('store restores session and tolerates corrupt data',()=>{
  const values=new Map();const storage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const store=new Store(storage);store.write('session',{demo:true});assert.deepEqual(store.read('session',null),{demo:true});
  values.set('snap-session','bad');assert.equal(store.read('session',null),null);store.clear('session');assert.equal(values.size,0);
});
test('PPV follows Android categories; does not claim schedule',()=>{assert.ok(ppvCategory('UFC PPV'));assert.ok(ppvCategory('Eventos'));assert.equal(ppvCategory('Noticias'),false);});
test('Xtream failover, account validation, stream escaping and episodes',async()=>{
  const old=globalThis.fetch;const requests=[];
  globalThis.fetch=async raw=>{
    const u=new URL(raw);requests.push(u);let data;
    if(u.pathname==='/config')data={servers:['https://bad.test','https://good.test']};
    else if(!u.searchParams.get('action'))data={user_info:{auth:u.hostname==='bad.test'?0:1,status:'Active'}};
    else if(u.searchParams.get('action')==='get_series_info')data={episodes:{1:[{id:'12',title:'Episode',container_extension:'mp4'},{id:'../bad'}]}};
    return {ok:true,text:async()=>JSON.stringify(data)};
  };
  try {
    const panel=new Panel({appConfigUrl:'https://config.test/config'});await panel.login('a/b','p?&');
    assert.equal(panel.line.server,'https://good.test');assert.equal(panel.stream('live','42','mp4'),'https://good.test/live/a%2Fb/p%3F%26/42.m3u8');
    const eps=await panel.episodes({seriesId:'8'});assert.equal(eps.length,1);assert.equal(requests.at(-1).searchParams.get('series_id'),'8');
    assert.throws(()=>panel.stream('live','../42','mp4'));
  }finally{globalThis.fetch=old;}
});
test('gateway revalidates persisted token and rejects inactive sessions',async()=>{
  const old=globalThis.fetch;
  globalThis.fetch=async(raw,options)=>({ok:true,text:async()=>JSON.stringify(raw.endsWith('/session')?{active:true,token:'test-token'}:{active:false})});
  try {const p=new Panel({gatewayUrl:'https://gateway.test'});assert.deepEqual(await p.login('demo','test'),{token:'test-token'});await assert.rejects(p.restore('test-token'));assert.equal(p.token,null);}finally{globalThis.fetch=old;}
});
