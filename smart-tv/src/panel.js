import {serverOrigin, ppvCategory} from './core.js';
export async function json(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  try {
    const response = await fetch(url, {...options, signal: controller.signal, redirect: 'error', cache: 'no-store'});
    if (!response.ok) throw new Error('Servidor no disponible');
    const body = await response.text();
    if (body.length > 20000000) throw new Error('Respuesta demasiado grande');
    return JSON.parse(body);
  } catch (_) { throw new Error('No se pudo conectar. Revisa red, HTTPS y permisos CORS.'); }
  finally { clearTimeout(timer); }
}
const numeric = value => /^\d+$/.test(String(value));
export class Panel {
  constructor(config) { this.config = config; this.line = null; this.token = null; }
  async gateway(path, payload) {
    const base = new URL(this.config.gatewayUrl);
    if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash) throw new Error('Gateway HTTPS inválido');
    return json(base.href.replace(/\/$/, '') + path, {method: 'POST', headers: {'Content-Type':'application/json', ...(this.token ? {'Authorization':'Bearer ' + this.token} : {})}, body: JSON.stringify(payload)});
  }
  async login(username, password) {
    if (!username || !password) throw new Error('Completa usuario y contraseña');
    if (this.config.gatewayUrl) {
      const r = await this.gateway('/session', {username, password});
      if (!r.token || r.active !== true) throw new Error('Línea no válida o inactiva');
      this.token = r.token; return {token:r.token};
    }
    const config = await json(this.config.appConfigUrl);
    const servers = config.servers || [config.server];
    if (!Array.isArray(servers) || !servers.length || servers.length > 8) throw new Error('Configuración inválida');
    for (const raw of servers) {
      try {
        const server = serverOrigin(raw);
        this.line = {server, username, password};
        const root = await this.request(''); const info = root.user_info;
        if (info && Number(info.auth) === 1 && String(info.status).toLowerCase() === 'active' && (!Number(info.exp_date) || Number(info.exp_date)*1000 > Date.now())) return {};
      } catch (_) { /* Try the next configured server without logging credentials. */ }
    }
    this.line = null; throw new Error('No se pudo validar una línea activa. Revisa la cuenta, HTTPS y CORS.');
  }
  async restore(token) {
    this.token = token;
    const r = await this.gateway('/session/validate', {});
    if (r.active !== true) { this.token = null; throw new Error('La sesión expiró'); }
  }
  async logout() { try { if (this.token) await this.gateway('/session/revoke', {}); } finally {this.line = null; this.token = null;} }
  async request(action, extra = {}) {
    const {server, username, password} = this.line;
    const url = new URL(server + '/player_api.php');
    url.searchParams.set('username',username); url.searchParams.set('password',password);
    if (action) url.searchParams.set('action',action);
    Object.keys(extra).forEach(k => url.searchParams.set(k,extra[k]));
    return json(url.href);
  }
  stream(kind, id, extension) {
    if (!numeric(id)) throw new Error('Identificador inválido');
    const {server, username, password} = this.line;
    const ext = String(extension || 'mp4').replace(/[^a-z0-9]/gi,'') || 'mp4';
    return server + '/' + kind + '/' + encodeURIComponent(username) + '/' + encodeURIComponent(password) + '/' + id + '.' + (kind === 'live' ? 'm3u8' : ext);
  }
  async catalog(section) {
    if (this.token) { const r = await this.gateway('/catalog', {section}); return this.validateItems(r.items); }
    const kind = section === 'Películas' ? 'vod' : section === 'Series' ? 'series' : 'live';
    const groups = await this.request('get_' + kind + '_categories');
    if (!Array.isArray(groups)) throw new Error('Categorías inválidas');
    const result = [];
    for (const group of groups) {
      if (!numeric(group.category_id) || (section === 'PPV HOY' && !ppvCategory(group.category_name))) continue;
      const list = await this.request('get_' + (kind === 'series' ? 'series' : kind + '_streams'), {category_id:group.category_id});
      if (!Array.isArray(list)) throw new Error('Catálogo inválido');
      list.forEach(j => {
        const id = kind === 'series' ? j.series_id : j.stream_id;
        if (!numeric(id)) return;
        result.push({id:kind + id, title:String(j.name || 'Sin título'), section, description:String(group.category_name || ''), seriesId:kind === 'series' ? String(id) : null,
          url:kind === 'series' ? '' : this.stream(kind === 'vod' ? 'movie' : 'live',id,j.container_extension)});
      });
    }
    return result;
  }
  validateItems(items) {
    if (!Array.isArray(items)) throw new Error('Catálogo inválido');
    return items.map(i => {
      if (!i.id || !i.title || (i.url && new URL(i.url).protocol !== 'https:')) throw new Error('Contenido inválido');
      return {...i, id:String(i.id), title:String(i.title)};
    });
  }
  async episodes(item) {
    if (this.token) { const r = await this.gateway('/episodes', {seriesId:item.seriesId}); return this.validateItems(r.items); }
    const root = await this.request('get_series_info', {series_id:item.seriesId});
    const result = [];
    Object.keys(root.episodes || {}).sort((a,b)=>Number(a)-Number(b)).forEach(season => {
      (root.episodes[season] || []).forEach(e => {if(numeric(e.id)) result.push({id:'episode'+e.id, title:e.title || 'Episodio '+e.episode_num, section:'Series', description:'Temporada '+season, url:this.stream('series',e.id,e.container_extension)});});
    }); return result;
  }
}
