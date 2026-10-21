import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {createHash} from 'node:crypto';
const origin='https://api.snaptvnow.com',repo='juancanta89-tech/SnapTvNow';
const temp=mkdtempSync(join(process.env.RUNNER_TEMP||'/tmp','boxpanel-'));
const run=(cmd,args,options={})=>execFileSync(cmd,args,{encoding:'utf8',stdio:['ignore','pipe','pipe'],maxBuffer:20*1024*1024,...options});
async function token(){
 const url=new URL(process.env.ACTIONS_ID_TOKEN_REQUEST_URL);url.searchParams.set('audience','snaptvnow-boxpanel-build');
 const r=await fetch(url,{headers:{Authorization:'Bearer '+process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN},signal:AbortSignal.timeout(15000)});if(!r.ok)throw Error('OIDC unavailable');const v=await r.json();if(typeof v.value!=='string')throw Error('OIDC unavailable');return v.value;
}
async function api(action,body={}){
 const r=await fetch(origin+'/api/boxpanel/builds/'+action,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+await token()},body:JSON.stringify(body),signal:AbortSignal.timeout(25000)});
 if(!r.ok)throw Error('BoxPanel '+action+' rejected ('+r.status+')');return r.json();
}
const tools=join(process.env.ANDROID_HOME,'build-tools/35.0.0');
function inspect(apk,job){
 const signed=run(join(tools,'apksigner'),['verify','--verbose','--print-certs',apk]);
 const certificate=signed.match(/Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})/i)?.[1]?.toLowerCase();
 const info=run(join(tools,'aapt'),['dump','badging',apk]);
 const packageName=info.match(/package: name='([^']+)'/)?.[1],code=Number(info.match(/versionCode='(\d+)'/)?.[1]);
 if(!certificate||packageName!==job.config.packageName||code!==job.versionCode)throw Error('APK identity verification failed');
 const bytes=readFileSync(apk);if(!bytes.length||bytes.length>200*1024*1024)throw Error('APK size invalid');
 return {packageName,versionCode:code,certificate,sha256:createHash('sha256').update(bytes).digest('hex'),size:bytes.length};
}
try{
 for(let index=0;index<4;index++){
  const {job}=await api('claim');if(!job)break;
  try{
   const input=join(temp,'build.json');writeFileSync(input,JSON.stringify(job.config),{mode:0o600});
   run('python3',['scripts/prepare-boxpanel.py',input,process.cwd()]);
   run('gradle',[':app:assembleRelease','--no-daemon'],{env:{...process.env,BOXPANEL_BUILD_FILE:input},stdio:'inherit'});
   const apk=resolve('app/build/outputs/apk/release/app-release.apk'),built=inspect(apk,job);
   const tag=`boxpanel-${job.appId}-v${job.versionCode}`;
   let release=null;
   try{release=JSON.parse(run('gh',['api',`repos/${repo}/releases/tags/${tag}`]));}catch{}
   if(!release){
    const body=join(temp,'release.json');writeFileSync(body,JSON.stringify({tag_name:tag,target_commitish:process.env.GITHUB_SHA,name:'BOXPANEL '+job.config.versionName,body:'Aplicación de marca blanca aprobada por su administrador. Incluye componentes de código abierto; fuentes y licencias en este repositorio.',draft:true}));
    release=JSON.parse(run('gh',['api','--method','POST',`repos/${repo}/releases`,'--input',body]));
   }
   if(release.target_commitish!==process.env.GITHUB_SHA)throw Error('Release belongs to a different source revision');
   const existing=release.assets?.find(a=>a.name==='app-release.apk');
   let published=built;
   if(existing){
    const download=join(temp,'existing');run('gh',['release','download',tag,'--repo',repo,'--pattern','app-release.apk','--dir',download]);
    published=inspect(join(download,'app-release.apk'),job);
    if(published.certificate!==built.certificate)throw Error('Existing release signing key mismatch');
    rmSync(download,{recursive:true,force:true});
   }else run('gh',['release','upload',tag,apk,'--repo',repo]);
   // No asset is ever overwritten. A retry reuses verified bytes of that version.
   if(release.draft)run('gh',['api','--method','PATCH',`repos/${repo}/releases/${release.id}`,'-F','draft=false']);
   await api('complete',{jobId:job.id,...published,url:`https://github.com/${repo}/releases/download/${tag}/app-release.apk`,sourceSha:process.env.GITHUB_SHA});
   console.log('Verified and published one approved BoxPanel APK.');
  }catch(error){try{await api('fail',{jobId:job.id});}catch{}throw error;}
 }
}catch(error){console.error(error.message?.startsWith('BoxPanel ')?error.message:'BoxPanel build or publication failed; no unverified release was activated.');process.exitCode=1;}
finally{rmSync(temp,{recursive:true,force:true});rmSync('app/build/generated/boxpanel',{recursive:true,force:true});}
