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
