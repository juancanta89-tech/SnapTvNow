# SNAPTVNOW 1.0.27 (56)

- A single OkHttp connection pool is shared by media, login, catalog, guide and profile requests. API redirects remain disabled and JSON response sizes remain bounded.
- Healthy foreground live channel changes reuse ExoPlayer after removing channel listeners, closing metrics and the prior receipt, and stopping the old source. Failed decoders, incompatible audio modes, account changes, VOD and background closure still release the player.
- Live startup keeps the one-second media threshold. Resume after actual rendered playback stalls starts at three seconds, grows to at most eight seconds for repeated stalls and preserves the existing 32 MiB live byte target and live-offset cap. Deliberate pauses do not count as stalls.
- Transport learning is confirmed by a rendered frame, scoped to device/account credentials/channel, bounded to 64 entries and seven days. Persistence contains salted hashes, transport and timestamp only. A failed route is forgotten.
- TV focus prepares up to eight MediaItem metadata entries. No speculative media stream, decoder or CCF connection is opened.

Server permission decisions are returned by a targeted lookup beside durable catalog snapshots. They are tied to the current authorized provider scope and both title/category generations. Stale snapshots fall back to the existing refresh path. Authentication, current permissions, expiry and connection leases remain checked.

Validation includes connection reuse without credential header leakage, redirect/body bounds, transport persistence/expiry, late-transfer isolation, healthy-player cleanup and decoder/audio/lifecycle replacement regressions. CI also runs the existing real native audio tests. Actual Fire TV startup and sustained playback require device verification; server first bytes are not decoded first frames.
