"""Measure the software decoder independently against one current Gol Perú sample."""
from pathlib import Path
import tempfile
import time

import vlc

from alternatives import variants
from probe import decode, emit, media_url, ProbeError, sample


def main():
    for identifier in variants():
        try:
            data, metadata = sample(media_url(identifier, "m3u8"))
            tracks = decode(data)["tracks"]
            if metadata["fallback"] or not any(track["codec_name"] == "mp2" for track in tracks):
                continue
        except Exception as err:
            emit(stage="control_source", public_id=identifier, error=str(err) if isinstance(err, ProbeError) else type(err).__name__)
            continue
        with tempfile.TemporaryDirectory() as root:
            video = Path(root) / "segment.ts"
            video.write_bytes(data)
            instance = vlc.Instance("--quiet", "--intf=dummy", "--vout=dummy", "--aout=dummy", "--avcodec-hw=none", "--stats")
            media = instance.media_new_path(str(video))
            player = instance.media_player_new()
            player.set_media(media)
            player.play()
            stats = vlc.MediaStats()
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                time.sleep(0.2)
                media.get_stats(stats)
                if stats.displayed_pictures >= 8 and stats.played_abuffers >= 3:
                    break
                if player.get_state() in (vlc.State.Error, vlc.State.Ended):
                    break
            emit(stage="vlc_control", public_id=identifier, tracks=tracks,
                 displayed_pictures=stats.displayed_pictures, played_audio_buffers=stats.played_abuffers,
                 decoded_video=stats.decoded_video, decoded_audio=stats.decoded_audio)
            player.stop()
            player.release()
            media.release()
            instance.release()
            return
    raise ProbeError("no_original_mp2_segment")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        emit(stage="control_failed", error=str(error) if isinstance(error, ProbeError) else type(error).__name__)
        raise SystemExit(1)
