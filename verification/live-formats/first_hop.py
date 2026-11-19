"""Inspect only first response to an Apple media startup probe on three selected entries."""
import urllib.error
import urllib.parse
import urllib.request
from probe import channels, emit, media_url, ProbeError

class FirstHop(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None

def run():
    opener = urllib.request.build_opener(FirstHop())
    agent = "AppleCoreMedia/1.0.0.22G86 (iPhone; U; CPU OS 18_6 like Mac OS X; en_us)"
    for label, identifier in channels().items():
        original = media_url(identifier, "ts")
        expected = media_url(identifier, "m3u8")
        for method in ("HEAD", "GET"):
            req = urllib.request.Request(original, method=method,
                headers={"User-Agent":agent, "Range":"bytes=0-1", "Accept-Encoding":"identity"})
            try:
                try:
                    response = opener.open(req, timeout=20)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    location = response.headers.get("Location", "")
                    canonical = urllib.parse.urljoin(original, location) if location else ""
                    emit(stage="native_first_hop", case=label, public_id=identifier,
                         method=method, status=response.status, same_channel_hls=canonical == expected,
                         mime=response.headers.get_content_type(), cache_control=response.headers.get("Cache-Control", "")[:90])
            except Exception as err:
                emit(stage="native_first_hop", case=label, method=method, error=type(err).__name__)
if __name__ == "__main__":
    try:
        run()
    except Exception as error:
        emit(stage="http_probe_setup", error=str(error) if isinstance(error, ProbeError) else type(error).__name__)
        raise SystemExit(1)
