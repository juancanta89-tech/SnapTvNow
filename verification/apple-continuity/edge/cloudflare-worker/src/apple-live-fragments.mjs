// Only plain, independently addressable MPEG-TS media playlists are eligible.
// Encrypted, byterange, fMP4 and low-latency playlists retain their native path.
const segmentLimit = 8 * 1024 * 1024;

export function appleGolPeruChannel(title) {
  if (typeof title !== 'string' || title.length > 200) return false;
  const normalized = title.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').trim();
  return /^PER\s*[-:|]\s*GOL\s+PERU(?:\s*\((?:L|M|TV|W|SD|HD|FHD|UHD|4K|720P?|1080P?|2160P?)\))*$/i.test(normalized);
}

export async function appleFragmentPlaylist(text, sign) {
  if (!text.startsWith('#EXTM3U') || /^#EXT-X-(?:KEY|SESSION-KEY|MAP|BYTERANGE|PART|PRELOAD-HINT|SKIP|GAP|STREAM-INF|MEDIA:|I-FRAME|RENDITION-REPORT)/m.test(text)) return null;
  const lines = text.split(/\r?\n/);
  const base = lines.find(line => line.startsWith('#EXT-X-MEDIA-SEQUENCE:'));
  let sequence = base === undefined ? 0 : Number(base.slice(22));
  if (!Number.isSafeInteger(sequence) || sequence < 0) return null;
  const segments = [];
  let map = true, durationAt = -1;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line === '#EXT-X-DISCONTINUITY') map = true;
    if (line.startsWith('#EXTINF:')) {
      if (durationAt !== -1 || !/^#EXTINF:\d+(?:\.\d+)?,/.test(line)) return null;
      durationAt = i;
    }
    if (!line || line.startsWith('#')) continue;
    if (durationAt === -1 || !Number.isSafeInteger(sequence) || sequence > 0xffffffff ||
        segments.length >= 48 || /\.(?:m4s|mp4|aac|vtt|m3u8)(?:$|[?#])/i.test(line)) return null;
    segments.push({uri:line, index:i, durationAt, map, sequence});
    sequence++;
    durationAt = -1;
    map = false;
  }
  if (!segments.length || durationAt !== -1) return null;
  await Promise.all(segments.map(async segment => {
    const {uri, index, durationAt, map, sequence} = segment;
    const [media, init] = await Promise.all([sign(uri, sequence, 'media'), map ? sign(uri, sequence, 'init') : null]);
    lines[index] = media;
    if (map) lines[durationAt] = '#EXT-X-MAP:URI="' + init + '"\n' + lines[durationAt];
  }));
  const version = lines.findIndex(line => line.startsWith('#EXT-X-VERSION:'));
  if (version < 0) lines.splice(1, 0, '#EXT-X-VERSION:7');
  else lines[version] = '#EXT-X-VERSION:' + Math.max(7, Number(lines[version].slice(15)) || 7);
  return lines.join('\n');
}

async function readSegment(response, signal) {
  if (response.status !== 200 || !response.body || Number(response.headers.get('content-length')) > segmentLimit) {
    await response.body?.cancel();
    throw Error('invalid_apple_segment');
  }
  const reader = response.body.getReader();
  const parts = [];
  let bytes = 0;
  const timer = setTimeout(() => reader.cancel('read_timeout').catch(() => {}), 15000);
  let complete = false;
  try {
    const deadline = Date.now() + 15000;
    for (;;) {
      if (signal.aborted || Date.now() >= deadline) throw Error('invalid_apple_segment');
      const chunk = await reader.read();
      if (signal.aborted || Date.now() >= deadline) throw Error('invalid_apple_segment');
      if (chunk.done) break;
      bytes += chunk.value.length;
      if (bytes > segmentLimit) throw Error('invalid_apple_segment');
      parts.push(chunk.value);
    }
    if (!bytes) throw Error('invalid_apple_segment');
    const data = new Uint8Array(bytes);
    let at = 0;
    for (const part of parts) { data.set(part, at); at += part.length; }
    complete = true;
    return data;
  } finally {
    clearTimeout(timer);
    if (!complete) void reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

function rangeResponse(bytes, part, req) {
  const total = bytes.byteLength;
  const headers = {'content-type':part === 'init' ? 'video/mp4' : 'video/iso.segment', 'cache-control':'private, no-store', 'accept-ranges':'bytes'};
  let start = 0, end = total - 1;
  const range = req.headers.has('If-Range') ? null : req.headers.get('Range');
  if (range) {
    const match = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (match && (match[1] || match[2])) {
      if (!match[1]) start = Math.max(0, total - Number(match[2]));
      else {
        start = Number(match[1]);
        if (match[2]) end = Math.min(end, Number(match[2]));
      }
    } else start = total;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end) {
      return new Response(null, {status:416, headers:{...headers,'content-range':'bytes */' + total}});
    }
    headers['content-range'] = 'bytes ' + start + '-' + end + '/' + total;
  }
  headers['content-length'] = String(end - start + 1);
  return new Response(req.method === 'HEAD' ? null : bytes.subarray(start,end+1), {status:range ? 206 : 200, headers});
}

// This cache only lives inside one Worker isolate. Every hit follows the same
// lease and expiry checks as a cold request. Coalesce init and media requests.
export function createAppleFragments(convertSegment) {
  const cache = new Map();
  const pending = new Map();
  let retained = 0;
  let processing = Promise.resolve();
  function remove(key, entry) { retained -= entry.bytes; cache.delete(key); }
  return async function fragment(req, claim, fetchSource) {
    const part = claim.applePart, sequence = claim.appleSequence;
    if (!['init','media'].includes(part) || !Number.isInteger(sequence) || sequence < 0 || sequence > 0xffffffff) throw Error('invalid_apple_segment');
    const key = JSON.stringify([claim.sid, claim.lease_id, claim.request_id, claim.resource, sequence]);
    const now = Date.now();
    for (const [k,v] of cache) if (v.until <= now) remove(k,v);
    let result = cache.get(key)?.result;
    if (!result) {
      let job = pending.get(key);
      if (!job) {
        if (pending.size >= 4) return new Response(null,{status:503,headers:{'Retry-After':'1'}});
        job = processing.then(async () => {
          if (req.signal.aborted || claim.exp <= Date.now()) throw Error('invalid_apple_segment');
          const data = await readSegment(await fetchSource(), req.signal);
          const output = await convertSegment(data, {sequence});
          if (!(output.init instanceof Uint8Array) || !(output.media instanceof Uint8Array) || !output.init.length || !output.media.length ||
              output.init.byteLength + output.media.byteLength > segmentLimit + 262144) throw Error('invalid_apple_segment');
          const bytes = output.init.byteLength + output.media.byteLength;
          while (cache.size && (retained + bytes > segmentLimit + 262144 || cache.size >= 4)) {
            const [oldKey,entry] = cache.entries().next().value; remove(oldKey,entry);
          }
          retained += bytes;
          cache.set(key, {result:output, bytes, until:Math.min(claim.exp,Date.now()+30000)});
          return output;
        });
        processing = job.catch(() => {});
        pending.set(key,job);
      }
      try { result = await job; }
      finally { if (pending.get(key) === job) pending.delete(key); }
    }
    if (req.signal.aborted || claim.exp <= Date.now()) return new Response(null,{status:410});
    return rangeResponse(result[part], part, req);
  };
}
