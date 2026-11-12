#!/usr/bin/env python3
"""One authorized movie-catalogue observation; no media or player requests."""
import collections,json,os,re,shlex,time,urllib.request,urllib.parse,urllib.error
from pathlib import Path
from official import PACKAGE,USERNAME,API,CheckFailure,NoRedirect,command,limited_read
D=Path(os.environ["RUNNER_TEMP"])/"snaptvnow-native-probe"

def probe(mode,password=None):
    if mode not in ("fgt_login","fgt_vod_catalogue"):raise CheckFailure("catalogue_only_mode_required")
    args=["am","instrument","-w","-r","-e","mode",mode]
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

def run():
    password=os.environ.get("DIEGUITO2026_PASSWORD","")
    if not password:raise CheckFailure("secure_login_not_bound")
    report={"app":json.loads((D/"preparation.json").read_text()),"scope":"dieguito2026; movie catalogue only",
            "started_epoch_ms":int(time.time()*1000),"playback_attempts":0,"native_runs":1}
    try:
        command(["adb","install","-g",str(D/"official.apk")])
        command(["adb","install",str(D/"native-probe.apk")])
        command(["adb","logcat","-c"])
        report["login"]=probe("fgt_login",password)
        if report["login"].get("status")=="login_observed":report["native_vod"]=probe("fgt_vod_catalogue")
        else:report["native_vod"]={"status":"not_verified","reason":"login_not_observed"}
        report["parser"]=parser_evidence()
        # Read each metadata operation once, after the app's initial download.
        cat_report,cats=metadata("get_vod_categories",password)
        list_report,rows=metadata("get_vod_streams",password)
        report["api"]={"categories":cat_report,"movies":list_report,"shape":summarize(cats,rows)}
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
