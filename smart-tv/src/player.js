export function platform() {
  return window.SNAP_PLATFORM || (window.webapis && window.webapis.avplay ? 'tizen' : /webos|web0s/i.test(navigator.userAgent) ? 'webos' : 'browser');
}
export function registerRemote() {
  if (platform() === 'tizen' && window.tizen && window.tizen.tvinputdevice) {
    ['MediaPlay','MediaPause','MediaPlayPause','MediaStop','MediaRewind','MediaFastForward','ColorF0Red'].forEach(key => {
      try { window.tizen.tvinputdevice.registerKey(key); } catch (_) {}
    });
  }
}
export class Player {
  constructor(video, status, progress = {}) {this.progress = progress; this.video = video; this.status = status; this.active = false; this.generation = 0; this.paused = false; this.native = platform() === 'tizen' && window.webapis && window.webapis.avplay;}
  async open(item) {
    this.stop(); this.active = true; this.completed = false; this.lastSave = 0; this.item = item; const generation = this.generation;
    this.status('Cargando…');
    const resume = Number(this.progress.read?.(item)) || 0;
    if (this.native) {
      const av = window.webapis.avplay;
      try {
        av.open(item.url);
        av.setDisplayRect(0,0,1920,1080);
        av.setDisplayMethod('PLAYER_DISPLAY_MODE_LETTER_BOX');
        av.setListener({onbufferingstart:()=>this.status('Cargando…'), onbufferingcomplete:()=>this.status(''), onerror:()=>this.status('No se pudo reproducir. Comprueba el formato y la conexión.'), oncurrentplaytime:()=>this.saveProgress(), onstreamcompleted:()=>{this.progress.clear?.(item);this.completed=true;this.paused=true;this.status('Reproducción terminada');}});
        av.prepareAsync(()=>{if(this.active && generation === this.generation){av.play();if(resume>0 && resume<av.getDuration()/1000-10)av.seekTo(resume*1000);this.status('');}},()=>{if(generation === this.generation)this.status('No se pudo preparar el vídeo.');});
      } catch (_) {this.status('No se pudo abrir el reproductor Samsung.');}
    } else {
      this.video.onwaiting = () => this.status('Cargando…');
      this.video.onplaying = () => this.status('');
      this.video.onerror = () => this.status('Formato no compatible o conexión no disponible.');
      this.video.ontimeupdate = () => this.saveProgress();
      this.video.onloadedmetadata = () => {if(this.active && generation===this.generation && resume>0 && resume<this.video.duration-10)this.video.currentTime=resume;};
      this.video.onended = () => {this.progress.clear?.(item);this.completed=true;this.paused=true;this.status('Reproducción terminada');};
      this.video.src = item.url; this.video.load();
      try { await this.video.play(); } catch (_) { if(this.active && generation === this.generation)this.status('Pulsa Reproducir para iniciar o revisa el formato.'); }
    }
  }
  toggle(force) {
    if (!this.active) return;
    const pause = force === 'pause' ? true : force === 'play' ? false : !this.paused;
    try {
      if (this.native) window.webapis.avplay[pause?'pause':'play']();
      else if (pause) this.video.pause();
      else this.video.play().catch(()=>this.status('No se pudo reproducir.'));
      this.paused = pause;
    } catch (_) {this.status('Reproductor no preparado');}
  }
  seek(delta) {
    if (!this.active || ['TV en vivo','PPV HOY'].includes(this.item.section)) {this.status('No se puede adelantar una emisión en vivo');return;}
    try {
      if (this.native) {const av=window.webapis.avplay;av.seekTo(Math.max(0,Math.min(av.getDuration(),av.getCurrentTime()+delta*1000)));}
      else if (Number.isFinite(this.video.duration)) this.video.currentTime = Math.max(0,Math.min(this.video.duration,this.video.currentTime+delta));
    } catch (_) {this.status('No se pudo cambiar la posición');}
  }
  saveProgress(force = false) {
    if (!this.active || this.completed || ['TV en vivo','PPV HOY'].includes(this.item.section)) return;
    const now = Date.now();
    if (!force && now - this.lastSave < 5000) return;
    try {
      const seconds = this.native ? window.webapis.avplay.getCurrentTime()/1000 : this.video.currentTime;
      if (Number.isFinite(seconds) && seconds > 0) {this.progress.write?.(this.item, seconds);this.lastSave=now;}
    } catch (_) { /* Storage or device APIs may be unavailable. */ }
  }
  tracks(type) {
    try {
      if (this.native) return window.webapis.avplay.getTotalTrackInfo().filter(t=>t.type===type).map(t=>({index:t.index,label:(type==='AUDIO'?'Audio ':'Subtítulo ')+(t.index+1)}));
      const list = type==='AUDIO' ? this.video.audioTracks : this.video.textTracks;
      return Array.from(list || []).map((t,index)=>({index,label:t.label || t.language || (type==='AUDIO'?'Audio ':'Subtítulo ')+(index+1)}));
    } catch (_) {return [];}
  }
  selectTrack(type, index) {
    try {
      if (this.native) {
        if (type==='TEXT') window.webapis.avplay.setSilentSubtitle(index===-1);
        if (index!==-1) window.webapis.avplay.setSelectTrack(type,index);
      } else {
        const list = type==='AUDIO' ? this.video.audioTracks : this.video.textTracks;
        Array.from(list || []).forEach((t,n)=>{if(type==='AUDIO')t.enabled=n===index;else t.mode=n===index?'showing':'disabled';});
      }
      return true;
    } catch (_) {this.status('Esta pista no se puede seleccionar en este dispositivo');return false;}
  }
  stop() {
    this.saveProgress(true);
    this.generation++; this.active = false; this.paused = false;
    if (this.native) {try {window.webapis.avplay.stop();window.webapis.avplay.close();}catch (_) {}}
    else {this.video.pause();this.video.removeAttribute('src');this.video.load();}
  }
}
