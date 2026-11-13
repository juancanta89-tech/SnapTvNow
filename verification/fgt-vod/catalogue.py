#!/usr/bin/env python3
"""One authorized movie-catalogue observation; no media or player requests."""
import collections,json,os,re,shlex,time,urllib.request,urllib.parse,urllib.error
import sqlite3,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
from official import PACKAGE,USERNAME,API,CheckFailure,NoRedirect,command,limited_read
D=Path(os.environ["RUNNER_TEMP"])/"snaptvnow-native-probe"

def probe(mode,password=None):
    if mode not in ("fgt_login","fgt_vod_catalogue","fgt_vod_refresh"):raise CheckFailure("catalogue_only_mode_required")
    args=["am","instrument","-w","-r","-e","mode",mode,"-e","screen","Mobile"]
    if mode=="fgt_vod_refresh":args += ["-e","refresh_context","warm"]
    if password is not None:args+=["-e","password",password]
    args+=["com.snaptvnow.nativeprobe/.NativeProbe"]
    output=command(["adb","shell",shlex.join(args)],timeout=115)
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_RESULT: native_report="):
            return json.loads(line.partition("native_report=")[2])
    raise CheckFailure("native_report_missing")

def metadata(action,password):
    if action not in ("get_vod_categories","get_vod_streams"):raise CheckFailure("catalogue_only_action_required")
    query=urllib.parse.urlencode({"username":USERNAME,"password":password,"action":action})
    req=urllib.request.Request(API+"?"+query,headers={"Accept":"application/json","User-Agent":"IPTVSmartersPro","Cache-Control":"no-cache"})
    started=time.monotonic()
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=30) as response:
            raw=limited_read(response,80*1024*1024);status=response.status
        data=json.loads(raw)
        return {"status":status,"duration_ms":round((time.monotonic()-started)*1000),"bytes":len(raw),
                "is_array":isinstance(data,list),"rows":len(data) if isinstance(data,list) else None},data
    except urllib.error.HTTPError as error:return {"status":error.code,"state":"failed"},None
    except Exception:return {"state":"failed","reason":"catalogue_read_or_json_failed","duration_ms":round((time.monotonic()-started)*1000)},None

def summarize(categories,rows):
    if not isinstance(categories,list) or not isinstance(rows,list):return {"state":"catalogue_not_verified"}
    chosen=[str(x.get("category_id")) for x in categories if isinstance(x,dict) and re.sub(r"\s+"," ",str(x.get("category_name",""))).strip().upper()=="VOD-ESTRENOS 2026"]
    fields=("stream_id","name","category_id","added","rating","rating_5based","stream_icon","container_extension","stream_type")
    return {"target_category_ids":chosen,
        "rows_in_target_category":sum(isinstance(x,dict) and str(x.get("category_id")) in chosen for x in rows),
        "field_types":{f:dict(collections.Counter(type(x.get(f)).__name__ if f in x else "missing" for x in rows if isinstance(x,dict))) for f in fields},
        "missing_or_empty_names":sum(not isinstance(x,dict) or not isinstance(x.get("name"),str) or not x["name"].strip() for x in rows),
        "invalid_stream_ids":sum(not isinstance(x,dict) or not re.fullmatch(r"[0-9]{1,10}",str(x.get("stream_id",""))) for x in rows),
        "nonnumeric_added":sum(isinstance(x,dict) and x.get("added") is not None and not re.fullmatch(r"[0-9]+",str(x["added"])) for x in rows)}

def parser_evidence():
    # Raw logs and exception messages never leave this isolated runner.
    raw=command(["adb","logcat","-d","-v","brief"],timeout=15)
    codes=("JsonSyntaxException","JsonDataException","NumberFormatException","MalformedJsonException",
           "IllegalStateException","OutOfMemoryError","SQLiteException","CursorWindowAllocationException","SocketTimeoutException","SSLHandshakeException")
    found={c:len(re.findall(r"\b"+c+r"\b",raw)) for c in codes}
    frames=re.findall(r"\bat ((?:boxbr\.apksrebrand|f\.j\.a|com\.google\.gson|retrofit2|okhttp3)[A-Za-z0-9_.$]+)\(",raw)
    return {"exception_type_counts":{k:v for k,v in found.items() if v},"app_or_parser_methods":list(dict.fromkeys(frames))[:24]}

def private_bytes(relative,maximum):
    # Read only this isolated emulator's authorized test profile. Never print
    # or upload raw app preferences/database bytes or exception messages.
    allowed={"shared_prefs/loginPrefs.xml","shared_prefs/loginPrefsserverurl.xml","shared_prefs/auto_start.xml",
             "shared_prefs/loginprefsmultiuser.xml","databases/iptv_live_streams_tv.db"}
    if relative not in allowed:raise CheckFailure("private_read_scope_rejected")
    try:
        result=subprocess.run(["adb","exec-out","cat","/data/user/0/"+PACKAGE+"/"+relative],
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20)
    except (OSError,subprocess.TimeoutExpired):raise CheckFailure("private_read_not_available") from None
    if result.returncode or len(result.stdout)>maximum:raise CheckFailure("private_read_not_available")
    return result.stdout

def private_snapshot():
    try:
        prefs={}
        for name in ("loginPrefs","loginPrefsserverurl","loginprefsmultiuser","auto_start"):
            root=ET.fromstring(private_bytes("shared_prefs/"+name+".xml",256*1024))
            prefs[name]={x.get("name"):(x.get("value") if x.get("value") is not None else x.text or "") for x in root}
        login=prefs["loginPrefs"];profile=prefs["loginprefsmultiuser"]
        uid=int(profile.get("userID","-1"))
        if uid<0 or login.get("username")!=USERNAME:
            return {"state":"not_verified","reason":"authorized_local_profile_not_matched"}
        result={"state":"verified","authorized_profile_matched":True,
                "screen_mode":prefs["auto_start"].get("pref.screen_type") if prefs["auto_start"].get("pref.screen_type") in ("TV","Mobile") else "other"}
        for label,value in (("selected",prefs["loginPrefsserverurl"].get("serverUrlMAG","")),
                            ("login",login.get("serverUrl",""))):
            parsed=urllib.parse.urlsplit(value if "://" in value else "http://"+value)
            result[label+"_server_reference_present"]=bool(parsed.hostname)
            result[label+"_server_is_own_api"]=((parsed.hostname or "").lower()=="api.snaptvnow.com") if parsed.hostname else None
        # Fixed aggregate SQL only, with a read-only in-memory database. Raw
        # names, media URLs, credentials and rows never become job artifacts.
        db=sqlite3.connect(":memory:")
        try:
            db.deserialize(private_bytes("databases/iptv_live_streams_tv.db",64*1024*1024))
            db.execute("PRAGMA query_only=ON")
            result["movie_rows"]=db.execute("SELECT count(*) FROM iptv_live_streams WHERE stream_type='movie' AND user_id_referred=?",(uid,)).fetchone()[0]
            cats=[r[0] for r in db.execute("SELECT categoryID_movie FROM iptv_movie_category WHERE user_id_referred=? AND upper(trim(categoryname_movie))='VOD-ESTRENOS 2026' LIMIT 3",(uid,))]
            result["target_category_count"]=len(cats)
            result["target_movie_rows"]=sum(db.execute("SELECT count(*) FROM iptv_live_streams WHERE stream_type='movie' AND user_id_referred=? AND categoryID=?",(uid,cat)).fetchone()[0] for cat in cats)
            return result
        finally:db.close()
    except Exception:return {"state":"not_verified","reason":"bounded_local_snapshot_not_available"}

def install(apk,stage):
    try:
        result=subprocess.run(["adb","install","-g",str(apk)],stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE,text=True,timeout=60)
    except (OSError,subprocess.TimeoutExpired):raise CheckFailure(stage+"_command_unavailable") from None
    if result.returncode:
        code=re.search(r"\bINSTALL_[A-Z0-9_]+\b",result.stdout+result.stderr)
        raise CheckFailure(stage+"_"+(code.group(0) if code else "adb_install_failed"))

def process_identity():
    try:
        value=command(["adb","shell","pidof",PACKAGE],timeout=15).strip()
        return value if re.fullmatch(r"[0-9]+",value) else None
    except CheckFailure:return None

def process_evidence():
    evidence={}
    try:
        raw=command(["adb","shell","dumpsys","meminfo",PACKAGE],timeout=15)
        for key,pattern in (("total_pss_kb",r"TOTAL PSS:\s+(\d+)"),("total_rss_kb",r"TOTAL RSS:\s+(\d+)")):
            match=re.search(pattern,raw)
            if match:evidence[key]=int(match.group(1))
    except CheckFailure:evidence["memory_read"]="not_verified"
    try:
        raw=command(["adb","shell","dumpsys","activity","exit-info",PACKAGE],timeout=15)
        evidence["memory_limiter_exit_marker"]="MemoryLimiter:AnonSwap" in raw
    except CheckFailure:evidence["exit_read"]="not_verified"
    return evidence

def run():
    password=os.environ.get("DIEGUITO2026_PASSWORD","")
    if not password:raise CheckFailure("secure_login_not_bound")
    report={"app":json.loads((D/"preparation.json").read_text()),"scope":"dieguito2026; movie catalogue only",
            "started_epoch_ms":int(time.time()*1000),"playback_attempts":0,"native_runs":1}
    try:
        report["device_scope"]="Android emulator, Fold-like portrait viewport; warm refresh, not the owner's Samsung device"
        report["sdk"]=command(["adb","shell","getprop","ro.build.version.sdk"]).strip()
        report["stage"]="configure_viewport"
        command(["adb","shell","wm","size","1280x1536"])
        command(["adb","shell","wm","density","320"])
        command(["adb","shell","settings","put","system","accelerometer_rotation","0"])
        command(["adb","shell","settings","put","system","user_rotation","0"])
        report["stage"]="install_official_apk"
        install(D/"official.apk","official")
        report["stage"]="install_external_controller"
        install(D/"native-probe.apk","controller")
        command(["adb","logcat","-c"])
        report["stage"]="native_login"
        report["login"]=probe("fgt_login",password)
        if report["login"].get("status")=="login_observed":
            report["stage"]="native_initial_catalogue"
            report["native_vod"]=probe("fgt_vod_catalogue")
            report["parser_initial"]=parser_evidence()
            before=process_identity()
            report["process_initial"]=process_evidence()
            report["stored_initial"]={"state":"not_requested","reason":"preserve_warm_application_process"}
            command(["adb","logcat","-c"])
            report["stage"]="native_persisted_refresh"
            report["native_refresh"]=probe("fgt_vod_refresh")
            report["parser_refresh"]=parser_evidence()
            after=process_identity()
            report["same_app_process_during_refresh"]=bool(before and after and before==after)
            report["process_refresh"]=process_evidence()
            command(["adb","shell","am","force-stop",PACKAGE])
            # Optional aggregate snapshot only after native checks finish.
            # Failure to restart adbd must never block the app observation.
            report["stage"]="optional_local_snapshot"
            try:
                command(["adb","root"],timeout=15)
                command(["adb","wait-for-device"],timeout=15)
                command(["adb","shell","true"],timeout=15)
            except CheckFailure:report["local_snapshot_access"]="not_verified"
            report["stored_refresh"]=private_snapshot()
        else:report["native_vod"]={"status":"not_verified","reason":"login_not_observed"}
        report["direct_api_metadata_requests"]=0
        report["state"]="evidence_recorded"
    except CheckFailure as error:report.update(state="not_verified",reason=str(error))
    except Exception:report.update(state="not_verified",reason="catalogue_probe_failed")
    finally:
        report["finished_epoch_ms"]=int(time.time()*1000)
        (D/"vod-report.json").write_text(json.dumps(report,indent=2)+"\n")
        for p in (PACKAGE,"com.snaptvnow.nativeprobe"):
            try:command(["adb","shell","am","force-stop",p],timeout=15)
            except CheckFailure:pass
    print("Catalogue evidence recorded; playback attempts: 0.")
    if report.get("state")!="evidence_recorded":raise SystemExit(1)

if __name__=="__main__":run()


