# Selective ports beyond the contiguous review baseline

`UPSTREAM.json` records a contiguous reviewed range. Its reviewed-through value
is deliberately unchanged for this batch: intervening upstream commits have
not all been dispositioned. Consult this supplementary record before repeating
ports from the newer range. Commit titles alone do not establish equivalence.

## Gateway compatibility batch

Based on fork commit `5c87b6ddba691715a1af5434bfb6cc8359cc828c`.
These are downstream adaptations, not wholesale upstream merges:

- `1cdc0dbfc042b70e9a355aeed69bacdd957e3796`: selected gateway liveness and
  streaming-flush behavior; retain downstream single-use tickets, stale socket
  fencing, history ownership, and reasoning continuity.
- `fb0f93c000bafc76be0382434a9101941a2ed37b`: no standalone events-since patch
  required; this fork currently uses resume for replay. Resume open requests
  are admitted only through registered, scoped response IDs.
- `e4be8b146e693879985f977a6679a12316cb7db1`: same-ID gateway requests for
  approval, clarification, sudo, secrets, and minimal vault prompts. Replies
  bind to source profile, runtime session, socket, and connection generation;
  rejected writes retain prompts. No full vault settings/catalog port.
- `f94d1784d43d0229790755b43ec71d2b5ee46413` and
  `b2aa08de239c00eae430cebe76644de371851ade`: stale prompt dispatch protection,
  adapted with conversation-generation and atomic connection-bound sends,
  including redirect fallback and attachment preparation.
- `0a2c78213997dc1ba1bab83024b881b1cf2b04b2`: selected model-context correction.
  Accepted model changes invalidate the old model-specific maximum while
  retaining live occupancy; no restoration of upstream REST-polled metering.

Additional hardening includes exact-binding prompt replay/replacement,
malformed replay rejection, request admission cleanup on disconnect,
read-only locked clarification answers, masked transient credential inputs,
and independently visible, validated save-login origins.

The Coil migration remains owner-deferred. Kanban expansion, plugin catalog
installation, composer redesign, and broad upstream refactors are not included.
Live subagent/session visibility is a separate authorized follow-up batch.

## Low-risk maintenance batch (2026-09-26)

Reviewed against upstream `e4004decb4acd18273a19fca2b2f8d12b829ce8e`
(v1.30.0). `reviewed_through` is unchanged: most of the 156 intervening
commits build on upstream's tool-display and ChatViewModel refactors, which
this fork has not taken.

- `8c5a3a061f5207ead6b36b2d026a159a7f69a53b`: syntax highlighting for code
  cards moves off the main thread; applied to the downstream `CodeBlockCard`.
- `399892618f0359296eac6e7a9650ce5207666be4`: fixed-width embedded Git SHA for
  reproducible APKs, without the F-Droid rationale.
- `a2c9a025` and `f3023c74`: KSP 2.3.12 and LaTeX 1.5.5. New verification
  hashes were checked against Maven Central `.sha256` sidecars.
- The macOS `aapt2` artifact is now pinned (checked against Google Maven) so
  the verified build also runs on Apple silicon hosts.

Not applicable: `304a9018`, `a567c911`, `7290b300`, `e539d650`, and
`1ab30f1e` patch upstream's `ui/chat/tool/` engine, which does not exist
here; the downstream skill renderer already orders its success branch
correctly. The `sqlcipher` 4.19.0 and Coil 3.x bumps, the scoped-route
expansion in `303019b9`, and all feature work remain for owner review.

## Validation and release boundary

Independent specification and code-quality reviews were completed, including
corrections and targeted regression tests. Both-flavor unit tests, Android lint,
debug assemblies, release Kotlin compilation, formatting, and color checks
passed locally. Full-suite testing exposed an authentication test collector
leak; teardown now resets that singleton before the next test changes Main.

Connected-device tests and exact-head hosted continuous integration remain
release gates. This document is not evidence of publication. A release must
also verify the downloaded signed artifact, clean install, previous-release
upgrade, and public latest-release readback.
