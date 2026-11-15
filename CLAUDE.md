# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`AGENTS.md` is a relative symlink to this file; edit `CLAUDE.md` only.

## Workflow

- Commit directly to `main`; do not create branches or pull requests unless asked.
- Do not run Gradle builds or tests locally. Push to `main` and let the `Android CI` workflow run `testDebugUnitTest` and `assembleDebug assembleRelease`; watch it with `gh run watch <run-id> --exit-status` and download APKs with `gh run download <run-id> -D <dir>`.
- Never change `versionCode`/`versionName` or create a version tag without an explicit user request.
- Commit subjects use Conventional Commits in lowercase English, matching history: `feat:`, `fix:`, `ci:`, `test:`, `docs:`.

## Localization

- Every user-visible string belongs in `app/src/main/res/values/strings.xml` (English, default) and `app/src/main/res/values-zh-rCN/strings.xml` (Simplified Chinese); keep both files in sync.
- ViewModel and repository messages use `UiText` resource IDs (`util/UiText.kt`), not preformatted strings.
- Update `README.md` and `README.zh-CN.md` together when behavior or setup changes.

## Gotchas

- Sink stores target URLs percent-encoded, so server-side `/api/link/search` cannot match Chinese text. `SinkRepository.searchLinks` deliberately scans active links locally for non-ASCII queries; do not replace it with a direct server search.
- The Sink edit endpoint replaces the whole link. `EditLinkPayload.from` carries over advanced web-only fields; preserve that behavior when touching the editor.
