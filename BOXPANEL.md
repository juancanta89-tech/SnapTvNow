# BoxPanel Android / Fire TV

Each approved app has a permanent UUID and package
`com.snaptvnow.box.a<uuid-without-hyphens>`. Its private Android data and
FileProvider authority are independent of SNAP and other reseller packages.
The original SNAP build remains the default when BOXPANEL_BUILD_FILE is absent.

The same player supports Classic (top navigation and large categories), Cinema
(top navigation, hero and rows), and Compact TV (sidebar and a compact grid).
Approved name, logo, colors, launcher icon and TV banner are generated as data.
No provider or subscriber credentials are embedded in the branding asset.
BoxPanel builds do not send credentials to the global SNAP VPN/message service.

Runtime config, three DNS, eight images and updates come only from the exact
app UUID on api.snaptvnow.com. Drafts and unapproved changes are not delivered.
Missing/disabled apps cannot fall back to SNAP DNS. The foreground app checks
at launch and every five minutes. A failed verification expires after ten
minutes; suspension immediately invalidates a positive response when observed.
The Activity releases playback and closes pending account work when disabled.
This controls official apps, not an independently modified third-party player.

## Approved build and update flow

The web owner approves a saved proposal. The Worker stores its immutable build
snapshot and queues one build. A lightweight scheduled GitHub job checks every
30 minutes and runs only after Android source checks pass on the exact main SHA.
GitHub can delay scheduled runs and disables public-repository schedules after
60 days without repository activity; an authorized owner can also use Run
workflow on Build approved BoxPanel apps. No keep-alive commits are generated.
Signing retains the existing protected `production` environment and owner
approval; no secret or approval gate is removed. Its pending approval must be
completed in GitHub by an authorized environment reviewer.

The signing job uses GitHub OIDC: fixed repository/owner IDs, main, this exact
workflow, production environment and audience. No new long-lived API token is
required. It generates up to four queued apps, validates package/version and
signature, and uploads immutable APK bytes to versioned public GitHub Releases.
Only a verified callback with matching GitHub asset digest activates the panel
download and the update feed. The signing certificate must match the previous
published app. Source, third-party provenance and licenses remain available in
the repository; rebranding does not change third-party license obligations.

New approvals supersede unfinished builds. An old callback cannot publish
against a newer approval or a suspended app. Failed/expired builds require the
owner's explicit retry; no unbounded build retry loop is introduced.
The reseller's download section remains visible when its customization editor
is hidden. DNS and image edits are fetched at runtime without rebuilding APKs.

The installed app checks for newer versions using the existing updater. It
verifies package, increasing version, certificate, size and SHA-256 before the
system installer. Installation requires the device's permission/confirmation;
there is no silent-install promise. Existing SNAP packages keep their original
update endpoint, package ID and version number.

## Verification and remaining physical-device boundary

Robolectric covers all three actual Activity layouts, two identities and
revocation stopping playback. Workerd/SQLite covers isolated runtime config,
approval/build races, scoped images, increasing versions and publication checks.
The Android source workflow runs the existing real audio decoder emulator test
on a Linux KVM runner; this replaces a macOS emulator that could not boot.
Emulation is not a physical Fire TV/Android installation. Verify initial
installation, remote navigation, real provider playback, suspension and an
in-place signed update on connected physical devices before mass distribution.

No paid service or subscription is added. Standard GitHub-hosted runners are
used in this public repository; releases avoid paid artifact storage for APK
delivery. Existing Cloudflare usage can still be billed under its current plan.

## Eight-design catalog v2
All original IDs remain supported. Órbita TV (`classic`), Prisma Cine (`cinema`),
Mosaico TV (`compact`), Aura (`aura`), Panorama (`panorama`), Arena (`stadium`),
Noir (`noir`) and Horizonte (`horizon`) use distinct native home compositions.
The regular SNAPTVNOW build remains unchanged. Prism/Mosaic use a TV side rail;
other layouts use top navigation on TV and the existing mobile navigation.
Home posters use this reseller's actual catalog, with original vector scenery
as a loading/empty backdrop. No preview titles are inserted into real catalogs.
The optional approved `style` controls backdrop mood, spacing, and welcome text;
legacy configs without it keep defaults. Native parsing and APK preparation
validate the same style fields accepted by the server. Changes require a newly
approved/published version through the existing protected signing workflow.
