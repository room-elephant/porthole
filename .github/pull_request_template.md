<!--
Title: what the change does, as it should read in the release notes, which list PRs by title.
Label: one from .github/release.yml (feature/enhancement, fix/bug, chore/test/refactor, documentation/docs,
dependencies, or ignore-for-release), so the changelog files it in the right section.
Delete a section that does not apply rather than writing "N/A". These comments can stay; they do not render.
-->

## Why

<!-- The problem this solves, in a sentence or two. Link the issue it closes. -->

## Where it sits

<!-- What this is stacked on, and which part of a larger piece of work it is, e.g. "phase 3 of 5: dashboard". -->

**Departures:** <!-- What the implementation settled differently from what was agreed, and why. "None" is an answer. -->

## What changes

<!-- By behaviour, not by file: what someone running Porthole, or the next session, will find different. -->

## Contracts

<!-- Anything another party depends on: the API (with docs/openapi.yml regenerated), state.json and its schemaVersion,
configuration and environment variables, the image and its entrypoint. Say what an existing install must do. -->

## Verification

<!-- What was run, with its counts, and what proves the change rather than a green run: a test seen failing before the
fix, a run in `make lab`. Say what was not run, and why. -->
- Server, `mvn verify` (coverage gates): 
- Architecture and diagrams, `make -C server arch`: 
- Integration tests, `make server-it`: 
- Client, `make -C client lint test`: 
- Native: 

## Review

<!-- Reviews done, the findings fixed (with their commits), and the findings deferred, with why. -->

## Left for the owner

<!-- What only a person can check: real Docker hosts, Traefik, a phone, the native image. And how this lands: alone,
or with the rest of a stack. -->

## Checklist

- [ ] Label from `.github/release.yml`
- [ ] `CLAUDE.md` updated, if a rule it states changed
- [ ] `docs/openapi.yml` regenerated (`make -C server openapi`), if the API changed
- [ ] Native hints regenerated (`make server-native-hints`), if the change adds reflection
- [ ] `docs/architecture/` C4 model or package diagrams edited, and what `C3ArchTest` and `PackagesArchTest` rendered
      committed, if packages or their dependencies changed
- [ ] `docs/architecture/codecity.sh` run and its picture committed, if Java under `server/` changed
