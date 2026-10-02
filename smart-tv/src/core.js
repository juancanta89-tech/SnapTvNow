export const SECTIONS = ['TV en vivo', 'PPV HOY', 'Películas', 'Series', 'Favoritos', 'Buscar'];
export const DEMO_VIDEO = 'https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
export function demoCatalog() {
  const result = [];
  ['TV en vivo', 'PPV HOY', 'Películas', 'Series'].forEach((section, s) => {
    for (let n = 1; n <= (s === 0 ? 15 : 5); n++) result.push({
      id: 'demo-' + s + '-' + n, title: ['Canal de prueba', 'Evento simulado', 'Película de prueba', 'Serie de prueba'][s] + ' ' + n,
      section, url: s === 3 ? '' : DEMO_VIDEO, seriesId: s === 3 ? String(n) : null,
      description: 'Big Buck Bunny · Blender Foundation · CC BY 3.0. ' + (s < 2 ? 'Simulación; no es una emisión en directo ni un evento real.' : 'Contenido de demostración.')
    });
  });
  return result;
}
export function serverOrigin(raw) {
  const u = new URL(String(raw).trim());
  if (u.protocol !== 'https:' || u.username || u.password || !/^\/?$/.test(u.pathname) || u.search || u.hash) throw new Error('Se requiere un servidor HTTPS válido');
  return u.origin;
}
export function ppvCategory(name) { return /(ppv|evento|event|pay.per.view|ufc|boxeo)/i.test(name); }
export function selectItems(items, section, query, favorites) {
  const q = (query || '').toLocaleLowerCase();
  return items.filter(i => (section === 'Buscar' || (section === 'Favoritos' ? favorites.includes(i.id) : i.section === section)) && i.title.toLocaleLowerCase().includes(q));
}
export function nextGridIndex(index, key, length, columns = 5) {
  if (!length) return -1;
  if (key === 'ArrowLeft') return index % columns ? index - 1 : index;
  if (key === 'ArrowRight') return index % columns < columns - 1 ? Math.min(index + 1, length - 1) : index;
  if (key === 'ArrowUp') return index - columns;
  if (key === 'ArrowDown') return index + columns < length ? index + columns : index;
  return index;
}
export class Store {
  constructor(storage) { this.storage = storage; }
  read(key, fallback) { try { const v = JSON.parse(this.storage.getItem('snap-' + key)); return v === null ? fallback : v; } catch (_) { return fallback; } }
  write(key, value) { this.storage.setItem('snap-' + key, JSON.stringify(value)); }
  clear(key) { this.storage.removeItem('snap-' + key); }
}
