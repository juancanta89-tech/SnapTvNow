import {cpSync, mkdirSync,readFileSync,writeFileSync,rmSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve,join} from 'node:path';
const root=fileURLToPath(new URL('..',import.meta.url));
for(const platform of ['tizen','webos','browser']) {
  const dest=join(root,'dist',platform);rmSync(dest,{recursive:true,force:true});mkdirSync(dest,{recursive:true});cpSync(join(root,'src'),dest,{recursive:true});
  writeFileSync(join(dest,'platform.js'),`window.SNAP_PLATFORM = '${platform}';\n`);
  if(platform!=='browser')cpSync(join(root,'platforms',platform),dest,{recursive:true});
  let html=readFileSync(join(dest,'index.html'),'utf8');
  if(platform==='tizen')html=html.replace('<script src="platform.js">','<script src="$WEBAPIS/webapis/webapis.js"></script><script src="platform.js">');
  // Installable apps use a classic script; this also works without file:// module support.
  const modules=['core.js','panel.js','player.js','app.js'].map(name=>readFileSync(join(root,'src',name),'utf8').replace(/^import .*;\n/gm,'').replace(/export /g,''));
  writeFileSync(join(dest,'bundle.js'),'(function(){\n'+modules.join('\n')+'\n})();\n');
  html=html.replace('<script type="module" src="app.js"></script>','<script src="bundle.js"></script>');writeFileSync(join(dest,'index.html'),html);
  console.log('Built '+resolve(dest));
}
