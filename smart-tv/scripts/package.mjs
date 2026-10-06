import {execFileSync} from 'node:child_process';
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';

// This script never fabricates signed Samsung packages.
const target = process.argv[2] || 'sources';
mkdirSync('packages', {recursive:true});
const run = (cmd, args) => execFileSync(cmd, args, {stdio:'inherit'});
if (target === 'sources') {
  run('zip', ['-q', '-r', resolve('packages/SNAPTVNOW-TV-demo-sources.zip'), 'dist', 'README.md', 'PACKAGES.md', 'REAL-PANEL.md']);
} else if (target === 'webos') {
  run(process.env.ARES_PACKAGE || 'ares-package', ['dist/webos', '-o', 'packages']);
} else if (target === 'tizen') {
  const profile = process.env.TIZEN_CERT_PROFILE;
  if (!profile) throw new Error('Define TIZEN_CERT_PROFILE with a real Samsung certificate profile. No unsigned WGT is generated.');
  run(process.env.TIZEN_CLI || 'tizen', ['package', '-t', 'wgt', '-s', profile, '-o', resolve('packages'), '--', resolve('dist/tizen')]);
} else {
  throw new Error('Use sources, webos or tizen');
}
const {readdirSync} = await import('node:fs');
const sums = readdirSync('packages').filter(f => /\.(zip|ipk|wgt)$/.test(f)).sort().map(f => createHash('sha256').update(readFileSync('packages/'+f)).digest('hex')+'  '+f);
writeFileSync('packages/SHA256SUMS.txt', sums.join('\n')+'\n');
