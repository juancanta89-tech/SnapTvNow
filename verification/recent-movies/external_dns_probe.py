#!/usr/bin/env python3
"""Bound a real DNS stream while applying downstream backpressure for 70 seconds."""

import http.client
import json
import os
from pathlib import Path
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

from acceptance import CheckFailure, NoRedirect, OUTPUT_DIRECTORY, REQUESTED_TITLE, USERNAME
from range_probe import safe_headers, verified_target


INITIAL_BYTES = 64 * 1024
MAX_DRAIN_BYTES = 2 * 1024 * 1024
PAUSE_SECONDS = 70
IO_SECONDS = 15
RECEIVE_BUFFER_BYTES = 16 * 1024
USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"


def epoch_ms():
    return int(time.time() * 1000)


class BufferLimitedConnection(http.client.HTTPSConnection):
    """Apply a small kernel receive window without changing TLS verification."""

    observation = None

    def connect(self):
        super().connect()
        self.stream_socket = self.sock
        try:
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, RECEIVE_BUFFER_BYTES)
            self.observation["receive_buffer_effective_bytes"] = self.sock.getsockopt(
                socket.SOL_SOCKET, socket.SO_RCVBUF)
            self.observation["receive_buffer_tuned"] = True
        except OSError:
            self.observation["receive_buffer_tuned"] = False


class BufferLimitedHandler(urllib.request.HTTPSHandler):
    def __init__(self, observation):
        super().__init__()
        self.observation = observation
        self.connection = None

    def https_open(self, request):
        def create_connection(host, **kwargs):
            connection = BufferLimitedConnection(host, **kwargs)
            connection.observation = self.observation
            self.connection = connection
            return connection
        return self.do_open(create_connection, request, context=self._context)


def bounded_read(response, connection, maximum, seconds, on_bytes=None):
    total = 0
    eof = False
    deadline = time.monotonic() + seconds
    read = getattr(response, "read1", None) or response.read
    while total < maximum:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError
        if connection.stream_socket.fileno() >= 0:
            connection.stream_socket.settimeout(max(0.05, remaining))
        chunk = read(min(65536, maximum - total))
        if not chunk:
            eof = True
            break
        total += len(chunk)
        if on_bytes is not None:
            on_bytes(total)
    return total, eof


def pause_without_reading(seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        time.sleep(min(5, max(0, deadline - time.monotonic())))


def observe_stream(password):
    report = {
        "started_epoch_ms": epoch_ms(), "completed_epoch_ms": None,
        "status": None, "state": "not_observed", "headers": {},
        "requested_range": "bytes=0-", "receive_buffer_requested_bytes": RECEIVE_BUFFER_BYTES,
        "receive_buffer_tuned": False, "receive_buffer_effective_bytes": None,
        "initial_bytes": 0, "initial_eof": False, "pause_started_epoch_ms": None,
        "pause_finished_epoch_ms": None, "pause_duration_ms": None,
        "post_pause_bytes": 0, "post_pause_eof": False,
        "post_pause_maximum_bytes": MAX_DRAIN_BYTES,
        "interpretation_limit": "transport_prefetch_can_hide_an_upstream_close",
    }
    response = None
    handler = BufferLimitedHandler(report)
    phase = "open"
    try:
        # The URL and all unfiltered response content remain private to this process.
        url = ("https://api.snaptvnow.com/movie/" + urllib.parse.quote(USERNAME, safe="")
               + "/" + urllib.parse.quote(password, safe="") + "/38111.mkv")
        request = urllib.request.Request(url, headers={
            "Range": "bytes=0-", "Accept-Encoding": "identity", "User-Agent": USER_AGENT,
        })
        opener = urllib.request.build_opener(NoRedirect(), handler)
        try:
            response = opener.open(request, timeout=IO_SECONDS)
        except urllib.error.HTTPError as error:
            report["status"] = error.code if 100 <= error.code <= 599 else None
            error.close()
            report["state"] = "http_error_observed"
            return report
        report["status"] = response.getcode()
        report["headers"] = safe_headers(response.headers)
        report["headers_epoch_ms"] = epoch_ms()
        phase = "initial_read"
        report["initial_bytes"], report["initial_eof"] = bounded_read(
            response, handler.connection, INITIAL_BYTES, IO_SECONDS,
            lambda value: report.update(initial_bytes=value))
        if report["initial_bytes"] != INITIAL_BYTES or report["initial_eof"]:
            report["state"] = "initial_body_incomplete"
            return report
        phase = "pause"
        report["pause_started_epoch_ms"] = epoch_ms()
        pause_started = time.monotonic()
        pause_without_reading(PAUSE_SECONDS)
        report["pause_duration_ms"] = round((time.monotonic() - pause_started) * 1000)
        report["pause_finished_epoch_ms"] = epoch_ms()
        phase = "post_pause_read"
        report["post_pause_bytes"], report["post_pause_eof"] = bounded_read(
            response, handler.connection, MAX_DRAIN_BYTES, IO_SECONDS,
            lambda value: report.update(post_pause_bytes=value))
        report["state"] = ("post_pause_eof_observed" if report["post_pause_eof"]
                           else "bounded_post_pause_bytes_observed")
    except CheckFailure:
        report["reason"] = "stream_redirect_rejected"
    except (TimeoutError, socket.timeout):
        report["reason"] = "stream_io_timeout"
    except urllib.error.URLError:
        report["reason"] = "stream_request_unavailable"
    except (http.client.IncompleteRead, ConnectionError, OSError):
        report["reason"] = "stream_transport_failed"
    except Exception:
        report["reason"] = "stream_observation_failed"
    finally:
        if "reason" in report:
            report["state"] = "transport_observation_incomplete"
            report["failure_phase"] = phase
        if response is not None:
            try:
                response.close()
            except Exception:
                pass
        report["completed_epoch_ms"] = epoch_ms()
    return report


def main():
    os.umask(0o077)
    report = {"resource": "38111", "title": REQUESTED_TITLE, "test_account": USERNAME,
              "client": "Python HTTPS with VLC User-Agent", "state": "not_observed"}
    try:
        directory = Path(os.environ["RUNNER_TEMP"]) / OUTPUT_DIRECTORY
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        verified_target(directory)
        if os.environ.get("GITHUB_ACTIONS") != "true":
            raise CheckFailure("external_secure_runner_required")
        password = os.environ.get("DIEGUITO2026_PASSWORD", "")
        if not password:
            raise CheckFailure("external_secure_login_not_bound")
        report.update(observe_stream(password))
    except CheckFailure as error:
        report["reason"] = str(error)
    except Exception:
        report["reason"] = "external_observation_failed"
    try:
        (directory / "dns-buffer-pause-report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    except Exception:
        print("::error::external_report_not_written")
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    # This is an observation, not a playback acceptance gate. EOF/errors remain
    # in the report; a full bounded drain cannot prove unbuffered server health.
    return 0 if report.get("pause_duration_ms", 0) >= PAUSE_SECONDS * 1000 else 1


if __name__ == "__main__":
    raise SystemExit(main())
