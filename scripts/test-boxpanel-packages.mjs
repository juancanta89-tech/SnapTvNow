import {execFileSync} from 'node:child_process';
import {mkdirSync,writeFileSync,copyFileSync,rmSync,readFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
import assert from 'node:assert/strict';
const folder=resolve('app/build/boxpanel-fixtures');mkdirSync(folder,{recursive:true});
const ids=['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222'];
const certificate=[];
try{
 for(const [name,id,version,design] of [['a1',ids[0],1,'classic'],['b1',ids[1],1,'cinema'],['a2',ids[0],2,'compact']]){
  const config={appId:id,packageName:'com.snaptvnow.box.a'+id.replaceAll('-',''),versionCode:version,versionName:'1.0.'+version,name:'Marca '+name,design,accent:'#16d4cd',background:'#07151f',logo:''};
  const input=join(folder,'input.json');writeFileSync(input,JSON.stringify(config));execFileSync('python3',['scripts/prepare-boxpanel.py',input,process.cwd()],{stdio:'inherit'});
  execFileSync('gradle',[':app:assembleDebug','--no-daemon'],{env:{...process.env,BOXPANEL_BUILD_FILE:input},stdio:'inherit'});
  const apk=join(folder,name+'.apk');copyFileSync('app/build/outputs/apk/debug/app-debug.apk',apk);
  const tools=join(process.env.ANDROID_HOME,'build-tools/35.0.0');
  const badging=execFileSync(join(tools,'aapt'),['dump','badging',apk],{encoding:'utf8'});assert.ok(badging.includes("name='"+config.packageName+"'"));assert.ok(badging.includes("versionCode='"+version+"'"));
  assert.deepEqual(JSON.parse(execFileSync('unzip',['-p',apk,'assets/boxpanel.json'],{encoding:'utf8'})),config);
  const signed=execFileSync(join(tools,'apksigner'),['verify','--print-certs',apk],{encoding:'utf8'});certificate.push(signed.match(/Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})/)[1]);
 }
 assert.equal(new Set(certificate).size,1,'fixtures use one stable debug signing certificate');
 console.log('PASS: two distinct compiled packages, all three designs, generated Android/TV branding, and an increasing same-package signed update.');
}finally{rmSync('app/build/generated/boxpanel',{recursive:true,force:true});rmSync(join(folder,'input.json'),{force:true});}
