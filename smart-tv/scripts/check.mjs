import {readFileSync,existsSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
for(const file of ['core','panel','player','app','config'])execFileSync(process.execPath,['--check','src/'+file+'.js'],{stdio:'inherit'});
for(const platform of ['tizen','webos','browser']) {
  const folder='dist/'+platform;
  if(!existsSync(folder))throw new Error('Run npm run build first');
  execFileSync(process.execPath,['--check',folder+'/bundle.js'],{stdio:'inherit'});
  const html=readFileSync(folder+'/index.html','utf8');
  for(const match of html.matchAll(/(?:src|href)="([^"$]+)"/g))if(!existsSync(folder+'/'+match[1]))throw new Error('Missing '+match[1]);
  if(platform!=='browser' && !existsSync(folder+'/icon.png'))throw new Error('Missing icon');
}
console.log('Syntax, bundles and referenced assets OK');
