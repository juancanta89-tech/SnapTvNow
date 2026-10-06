import {SECTIONS,demoCatalog,DEMO_VIDEO,selectItems,nextGridIndex,Store} from './core.js';
import {Panel} from './panel.js';
import {Player,platform,registerRemote} from './player.js';
const $ = id => document.getElementById(id);
const config = window.SNAP_CONFIG;
const store = new Store(localStorage), panel = new Panel(config);
let session=null, section=SECTIONS[0], items=[], query='', episodes=null, currentItem=null, returnId=null, requestId=0;
let favoriteData=store.read('favorites',{});
if (!favoriteData || typeof favoriteData !== 'object' || Array.isArray(favoriteData)) favoriteData={};
const progress=store.read('progress',{});
const positions=progress && typeof progress==='object' && !Array.isArray(progress)?progress:{};
const player=new Player($('video'), message => {$('play-status').textContent=message;}, {
  read:item=>positions[item.id],
  write:(item,seconds)=>{positions[item.id]=seconds;try {store.write('progress',positions);}catch(_){}},
  clear:item=>{delete positions[item.id];try {store.write('progress',positions);}catch(_){} }
});
let searchScope='Todo', trackIndex={AUDIO:-1,TEXT:-1};
const text = (tag, value, className) => {const el=document.createElement(tag);el.textContent=value;if(className)el.className=className;return el;};
function button(label, action, id) {const b=text('button',label);if(id)b.id=id;b.onclick=action;return b;}
function notice(message) {$('toast').textContent=message;clearTimeout(notice.timer);notice.timer=setTimeout(()=>{$('toast').textContent='';},5000);}
function favorites() {return Object.keys(favoriteData);}
function toggleFavorite(item) {
  if (favoriteData[item.id]) delete favoriteData[item.id];
  else favoriteData[item.id]={id:item.id,title:item.title,section:item.section,seriesId:item.seriesId || null,description:item.description || ''};
  // Never persist URLs containing subscriber credentials or signed stream URLs.
  try {store.write('favorites',favoriteData);notice(favoriteData[item.id]?'Añadido a favoritos':'Quitado de favoritos');}catch (_) {notice('No se pudo guardar el favorito');}
}
function loginView(message='') {
  session=null; $('account').textContent=''; $('app').replaceChildren();
  const box=text('div','','login');box.append(text('p','TU ENTRETENIMIENTO, EN PANTALLA GRANDE','eyebrow'),text('h1','Bienvenido a SNAPTVNOW'),text('p',config.demo?'Prueba todas las funciones con contenido de demostración autorizado.':'Accede con las credenciales de tu línea.'));
  const form=document.createElement('form');
  if(!config.demo) {
    ['Usuario','Contraseña'].forEach((label,n)=>{const l=text('label',label);l.htmlFor=n?'password':'username';const input=document.createElement('input');input.id=l.htmlFor;input.type=n?'password':'text';input.autocomplete=n?'current-password':'username';input.required=true;form.append(l,input);});
    if (!config.gatewayUrl) box.append(text('p','Conexión directa: la sesión dura hasta cerrar la app. Para recordar el acceso real, configura el gateway de sesiones.','notice'));
  }
  const enter=button(config.demo?'Entrar en demo':'Iniciar sesión',null,'login');enter.type='submit';form.append(enter);
  form.onsubmit=async e=>{
    e.preventDefault();enter.disabled=true;
    try {
      const result=config.demo?{demo:true}:await panel.login($('username').value.trim(),$('password').value);
      if(config.demo || result.token) store.write('session',result);
      session=result;items=config.demo?demoCatalog():[];render();await loadSection();
    }catch(error){notice(error.message);}finally{enter.disabled=false;}
  };
  box.append(form,text('p',message)); $('app').append(box);(config.demo?enter:$('username')).focus();
}
async function signOut() {
  requestId++;player.stop();$('player').hidden=true;
  store.clear('session');store.clear('favorites');store.clear('progress');Object.keys(positions).forEach(k=>delete positions[k]);favoriteData={};items=[];episodes=null;query='';section=SECTIONS[0];
  loginView();await panel.logout();
}
async function loadSection() {
  if(config.demo || !session) return;
  const ticket=++requestId;
  const requested=section;notice('Cargando catálogo…');
  try {
    const sections=['Favoritos','Buscar'].includes(requested)?SECTIONS.slice(0,4):[requested];
    const loaded=[];
    for(const s of sections) loaded.push(...await panel.catalog(s));
    if(ticket!==requestId || !session)return;
    const keys=new Set(loaded.map(i=>i.section));items=items.filter(i=>!keys.has(i.section) && !sections.includes(i.section)).concat(loaded);
    render();notice('Catálogo actualizado');
  }catch(error){if(ticket===requestId)notice(error.message);}
}
function render(focusId) {
  $('account').textContent=config.demo?'DEMO · CONTENIDO DE PRUEBA':'LÍNEA ACTIVA';
  const app=$('app');app.replaceChildren();
  const nav=document.createElement('nav');nav.setAttribute('aria-label','Secciones');
  SECTIONS.forEach((s,n)=>{const b=button(s,()=>{requestId++;section=s;query='';episodes=null;render('nav-'+n);loadSection();},'nav-'+n);b.setAttribute('aria-current',s===section?'page':'false');nav.append(b);});
  nav.append(button('Cerrar sesión',signOut,'logout'));app.append(nav);
  const hero=text('div','','hero');hero.append(text('p',config.demo?'VISTA PREVIA · SIN EVENTOS REALES':'TU CATÁLOGO','eyebrow'),text('h1',episodes?'Elige un episodio':section),text('p',config.demo?'Una experiencia pensada para tu TV. Vídeo de prueba: Big Buck Bunny, Blender Foundation.':'Elige un título y pulsa OK. Usa el botón rojo para guardarlo.'));
  if(section==='PPV HOY')hero.append(text('p','Categorías PPV del proveedor. El nombre no garantiza la fecha ni los derechos del evento.','notice'));
  app.append(hero);
  if(episodes)app.append(button('Volver a series',()=>{episodes=null;render();},'back-series'));
  if(section==='Buscar' && !episodes) {
    const input=document.createElement('input');input.type='search';input.placeholder='Buscar canales, películas y series';input.setAttribute('aria-label','Buscar');input.id='search';input.className='search';input.value=query;
    input.oninput=()=>{query=input.value;renderGrid();};app.append(input);
    const scopes=text('div','','actions');
    ['Todo','TV en vivo','Películas','Series'].forEach(scope=>{
      const b=button(scope,()=>{searchScope=scope;render('scope-'+scope);},'scope-'+scope);
      b.setAttribute('aria-pressed',String(searchScope===scope));scopes.append(b);
    });app.append(scopes);
  }
  app.append(text('div','','grid'));app.lastChild.id='grid';renderGrid();
  const target=$(focusId || (section==='Buscar'?'search':'nav-'+SECTIONS.indexOf(section)));if(target)target.focus();
}
function renderGrid() {
  const grid=$('grid');grid.replaceChildren();
  const visible=episodes || selectItems(items,section,query,favorites()).filter(i=>section!=='Buscar' || searchScope==='Todo' || i.section===searchScope || (searchScope==='TV en vivo' && i.section==='PPV HOY'));
  if(!visible.length){grid.append(text('p','No hay títulos disponibles.'));return;}
  visible.forEach((item,n)=>{
    const b=button('',()=>openItem(item),'card-'+n);b.className='card';b.dataset.index=n;b.dataset.item=item.id;
    b.append(text('span',(favoriteData[item.id]?'♥ ':'')+String(n+1).padStart(2,'0'),'mark'),text('span',item.title,'title'),text('span',config.demo?'DEMO · '+item.section:item.section,'caption'));
    b.onkeydown=e=>{if(e.keyCode===403 || e.key==='f'){e.preventDefault();toggleFavorite(item);renderGrid();const target=$('card-'+Math.min(n,$('grid').querySelectorAll('button').length-1));if(target)target.focus();}};
    grid.append(b);
  });
}
async function openItem(item) {
  if(item.seriesId) {
    const ticket=++requestId;
    try {
      const result=config.demo?[1,2,3].map(n=>({id:item.id+'-ep-'+n,title:item.title+' · Episodio '+n,section:'Series',description:item.description,url:DEMO_VIDEO})):await panel.episodes(item);
      if(ticket!==requestId || !session)return;
      episodes=result;render('card-0');
    }catch(error){notice(error.message);}return;
  }
  if(!item.url){notice('Fuente no disponible');return;}
  trackIndex={AUDIO:-1,TEXT:-1};returnId=document.activeElement.id;currentItem=item;$('playing-title').textContent=item.title;$('player').hidden=false;
  $('player-favorite').textContent=favoriteData[item.id]?'♥ Quitar favorito':'♡ Favorito';$('close-player').focus();await player.open(item);
}
function closePlayer() {player.stop();$('player').hidden=true;renderGrid();const target=$(returnId);if(target)target.focus();}
$('close-player').onclick=closePlayer;
$('toggle-player').onclick=()=>player.toggle();$('rewind').onclick=()=>player.seek(-10);$('forward').onclick=()=>player.seek(10);
$('player-favorite').onclick=()=>{toggleFavorite(currentItem);$('player-favorite').textContent=favoriteData[currentItem.id]?'♥ Quitar favorito':'♡ Favorito';};
$('retry-player').onclick=()=>player.open(currentItem);
function cycleTrack(type) {
  const tracks=player.tracks(type);
  if(!tracks.length){notice('La fuente o el dispositivo no ofrece pistas seleccionables');return;}
  const options=type==='TEXT'?[{index:-1,label:'Subtítulos desactivados'},...tracks]:tracks;
  trackIndex[type]=(trackIndex[type]+1)%options.length;
  const chosen=options[trackIndex[type]];
  if(player.selectTrack(type,chosen.index))notice(chosen.label);
}
$('audio-track').onclick=()=>cycleTrack('AUDIO');
$('subtitle-track').onclick=()=>cycleTrack('TEXT');
document.addEventListener('keydown',e=>{
  const active=document.activeElement;
  const back=e.key==='Escape' || e.keyCode===10009 || e.keyCode===461;
  if(back){e.preventDefault();if(!$('player').hidden)closePlayer();else if(episodes){episodes=null;render();}else if(session && section!==SECTIONS[0]){section=SECTIONS[0];query='';render();loadSection();}else if(session){const b=$('logout');if(b)b.focus();}return;}
  if(!$('player').hidden) {
    if([415,19,10252,413,412,417,403].includes(e.keyCode)) {
      e.preventDefault();if(e.keyCode===413)closePlayer();else if(e.keyCode===412)player.seek(-10);else if(e.keyCode===417)player.seek(10);else if(e.keyCode===403)$('player-favorite').click();else player.toggle(e.keyCode===415?'play':e.keyCode===19?'pause':undefined);return;
    }
  }
  if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key))return;
  if(active.tagName==='INPUT' && ['ArrowLeft','ArrowRight'].includes(e.key))return;
  e.preventDefault();
  if(active.classList.contains('card')) {
    const cards=Array.from($('grid').querySelectorAll('button'));const index=nextGridIndex(Number(active.dataset.index),e.key,cards.length);
    if(index<0){const target=$('search') || $('back-series') || $('nav-'+SECTIONS.indexOf(section));target.focus();}else cards[index].focus();
  }else {
    const root=$('player').hidden?$('app'):$('player');
    const controls=Array.from(root.querySelectorAll('button,input')).filter(b=>!b.disabled && !b.classList.contains('card'));
    const n=controls.indexOf(active);
    if(e.key==='ArrowDown' && $('player').hidden && $('card-0') && active.tagName!=='INPUT' && active.closest('nav')) {const target=$('search') || $('card-0');target.focus();}
    else if(e.key==='ArrowDown' && active.id==='search' && $('card-0'))$('card-0').focus();
    else {const target=controls[Math.max(0,Math.min(controls.length-1,n+(['ArrowLeft','ArrowUp'].includes(e.key)?-1:1)))];if(target)target.focus();}
  }
  document.activeElement.scrollIntoView({block:'nearest'});
});
document.addEventListener('visibilitychange',()=>{if(document.hidden && player.active)player.toggle('pause');});
window.addEventListener('pagehide',()=>player.stop());
registerRemote();const device=platform();document.body.classList.add(device);$('platform').textContent={tizen:'Samsung Tizen',webos:'LG webOS',browser:'Vista previa en navegador'}[device];
async function start() {
  const saved=store.read('session',null);
  if(config.demo && saved && saved.demo){session=saved;items=demoCatalog();render();return;}
  if(!config.demo && config.gatewayUrl && saved && saved.token) {
    try {await panel.restore(saved.token);session=saved;render();await loadSection();return;}catch(_){store.clear('session');}
  }
  loginView();
}
start().catch(()=>loginView('No se pudo recuperar la sesión.'));
