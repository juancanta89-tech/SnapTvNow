#!/usr/bin/env python3
"""Observe two bounded HTTP ranges through the normal customer DNS entry."""

import json
import os
from pathlib import Path
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

from acceptance import (CheckFailure, NoRedirect, OUTPUT_DIRECTORY, REQUESTED_TITLE,
                        USERNAME, normalize_title)


RESOURCE = "38111"
EXTENSION = "mkv"
MAX_BODY_BYTES = 1024
REQUEST_TIMEOUT = 15
MAX_INTEGER = (1 << 63) - 1
USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"
KNOWN_MEDIA_ERRORS = frozenset({
    "account_inactive", "adult_content_disabled", "ccf_capacity", "content_disabled",
    "credentials_required", "https_required", "invalid_credentials", "invalid_stream",
    "live_signal_unavailable", "live_startup_timeout", "media_origin_unapproved",
    "method_not_allowed", "not_found", "operation_not_allowed", "panel_unavailable",
    "playback_expired", "playback_superseded", "provider_inactive", "provider_not_configured",
    "provider_temporarily_unavailable", "provider_unavailable", "session_expired",
    "session_unavailable", "stream_unavailable", "title_not_allowed", "try_later",
    "upstream_unavailable", "user_connection_limit", "xtream_disabled", "xtream_unavailable",
})


def epoch_ms():
    return int(time.time() * 1000)


def integer_header(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9]{1,19}", value.strip()):
        return None
    number = int(value.strip())
    return number if number <= MAX_INTEGER else None


def content_range_header(value):
    if not isinstance(value, str) or len(value) > 100:
        return None
    value = value.strip()
    match = re.fullmatch(r"bytes (?:(\d{1,19})-(\d{1,19})|\*)/(\d{1,19}|\*)", value, re.I)
    if not match:
        return None
    start, end, total = match.groups()
    numbers = [int(number) for number in (start, end, total) if number not in (None, "*")]
    if any(number > MAX_INTEGER for number in numbers):
        return None
    if start is not None and (int(start) > int(end) or (total != "*" and int(end) >= int(total))):
        return None
    if start is None and total == "*":
        return None
    return value


def safe_headers(headers):
    mime = headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
    if not re.fullmatch(r"[a-z0-9!#$&^_.+-]{1,60}/[a-z0-9!#$&^_.+-]{1,60}", mime):
        mime = None
    accept_ranges = headers.get("Accept-Ranges", "").strip().lower()
    return {
        "mime": mime,
        "content_length": integer_header(headers.get("Content-Length")),
        "content_range": content_range_header(headers.get("Content-Range")),
        "accept_ranges": accept_ranges if accept_ranges in ("bytes", "none") else None,
    }


def response_total(result):
    content_range = result.get("content_range")
    if content_range:
        total = content_range.rsplit("/", 1)[1]
        if total != "*":
            return int(total)
    # A 206 Content-Length describes the selected range, not the whole movie.
    if result.get("status") == 200:
        return result.get("content_length")
    return None


def classify_prefix(body):
    if body.startswith(b"\x1a\x45\xdf\xa3"):
        return {"prefix_classification": "EBML"}
    if body.lstrip().startswith(b"{"):
        try:
            value = json.loads(body)
            code = value.get("error") if isinstance(value, dict) else None
            if isinstance(code, str) and code in KNOWN_MEDIA_ERRORS:
                return {"prefix_classification": "JSON_error_known", "known_error_code": code}
        except (ValueError, UnicodeError):
            pass
    # This category makes no claim that the prefix is decodable movie media.
    return {"prefix_classification": "binary"}


def verified_target(directory):
    path = directory / "movie-metadata.json"
    try:
        with path.open("rb") as source:
            encoded = source.read(2 * 1024 * 1024 + 1)
        if len(encoded) > 2 * 1024 * 1024:
            raise CheckFailure("range_metadata_not_verified")
        report = json.loads(encoded)
        targets = report.get("targets")
        if (report.get("state") != "metadata_observed" or report.get("test_account") != USERNAME
                or not isinstance(targets, list)):
            raise CheckFailure("range_metadata_not_verified")
        matches = [target for target in targets if isinstance(target, dict)
                   and target.get("resource") == RESOURCE]
        if len(matches) != 1:
            raise CheckFailure("range_target_not_verified")
        target = matches[0]
        detail = target.get("detail")
        if (target.get("kind") != "movie"
                or normalize_title(target.get("title")) != normalize_title(REQUESTED_TITLE)
                or not isinstance(target.get("listing_extension"), str)
                or target["listing_extension"].strip().lower() != EXTENSION
                or not isinstance(detail, dict) or detail.get("state") != "metadata_observed"
                or not isinstance(detail.get("extension"), str)
                or detail["extension"].strip().lower() != EXTENSION):
            raise CheckFailure("range_target_not_verified")
        checked = report.get("checked_epoch_ms")
        if (isinstance(checked, bool) or not isinstance(checked, int)
                or not -120000 <= epoch_ms() - checked <= 15 * 60 * 1000):
            raise CheckFailure("range_metadata_not_current")
    except CheckFailure:
        raise
    except Exception:
        raise CheckFailure("range_metadata_not_verified") from None


def probe_range(password, start, end):
    result = {
        "requested_start": start, "requested_end": end,
        "started_epoch_ms": epoch_ms(), "headers_epoch_ms": None,
        "completed_epoch_ms": None, "status": None,
        "mime": None, "content_length": None, "content_range": None,
        "accept_ranges": None, "bytes_read": 0, "state": "not_observed",
        "prefix_classification": None,
    }
    response = None
    body = bytearray()
    deadline = time.monotonic() + REQUEST_TIMEOUT
    try:
        # Keep the credential-bearing URL in memory only. Never log requests or exceptions.
        url = ("https://api.snaptvnow.com/movie/" + urllib.parse.quote(USERNAME, safe="")
               + "/" + urllib.parse.quote(password, safe="") + "/" + RESOURCE + "." + EXTENSION)
        request = urllib.request.Request(url, method="GET", headers={
            "Range": f"bytes={start}-{end}", "User-Agent": USER_AGENT,
            "Accept-Encoding": "identity",
        })
        opener = urllib.request.build_opener(NoRedirect())
        try:
            response = opener.open(request, timeout=REQUEST_TIMEOUT)
        except urllib.error.HTTPError as error:
            # HTTP errors still provide useful, whitelisted response metadata.
            response = error
        result["headers_epoch_ms"] = epoch_ms()
        status = response.getcode()
        if isinstance(status, int) and 100 <= status <= 599:
            result["status"] = status
        result.update(safe_headers(response.headers))
        # In particular, a 200 response is never skipped forward to the requested
        # offset: read at most 1024 bytes and close, even if Range was ignored.
        read = getattr(response, "read1", None) or response.read
        while len(body) < MAX_BODY_BYTES:
            if time.monotonic() >= deadline:
                raise TimeoutError
            chunk = read(MAX_BODY_BYTES - len(body))
            if not chunk:
                break
            body.extend(chunk)
            result["bytes_read"] = len(body)
        result.update(classify_prefix(body))
        result["state"] = "response_observed"
    except CheckFailure:
        result["reason"] = "range_redirect_rejected"
    except (TimeoutError, socket.timeout):
        result["reason"] = "range_request_timeout"
    except urllib.error.URLError:
        result["reason"] = "range_request_unavailable"
    except Exception:
        result["reason"] = "range_probe_failed"
    finally:
        if body and result["prefix_classification"] is None:
            result.update(classify_prefix(body))
        if response is not None:
            try:
                response.close()
            except Exception:
                pass
        result["completed_epoch_ms"] = epoch_ms()
    return result


def run(directory):
    report = {
        "started_epoch_ms": epoch_ms(), "completed_epoch_ms": None,
        "test_account": USERNAME, "resource": RESOURCE, "title": REQUESTED_TITLE,
        "extension": EXTENSION, "state": "not_observed", "requests": [],
    }
    try:
        verified_target(directory)
        if os.environ.get("GITHUB_ACTIONS") != "true":
            raise CheckFailure("range_secure_runner_required")
        password = os.environ.get("DIEGUITO2026_PASSWORD", "")
        if not password:
            raise CheckFailure("range_secure_login_not_bound")
        first = probe_range(password, 0, 1023)
        report["requests"].append(first)
        total = response_total(first)
        if total is not None and total > 1049599:
            report["requests"].append(probe_range(password, 1048576, 1049599))
        else:
            report["second_request_skipped"] = ("range_total_not_verified" if total is None
                                                else "range_total_too_small")
        observed = [result["state"] == "response_observed" for result in report["requests"]]
        report["state"] = ("response_observed" if all(observed) else
                           "partially_observed" if any(observed) else "not_observed")
    except CheckFailure as error:
        # All codes raised here are fixed literals; no upstream exception is serialized.
        report["reason"] = str(error)
    except Exception:
        report["reason"] = "range_probe_failed"
    report["completed_epoch_ms"] = epoch_ms()
    (directory / "range-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    return report


def main():
    os.umask(0o077)
    try:
        directory = Path(os.environ["RUNNER_TEMP"]) / OUTPUT_DIRECTORY
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        report = run(directory)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["state"] == "response_observed" else 1
    except Exception:
        print("::error::range_report_not_written")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
