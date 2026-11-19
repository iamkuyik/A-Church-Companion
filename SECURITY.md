# Security Policy

## Privacy & Permission Posture
**A Church Companion (ACC)** is designed as a strictly offline Android application:
- Requests **zero** `INTERNET` or runtime dangerous permissions.
- All scripture queries, hymnal search, bookmarks, and PDF rendering execute locally on-device.
- `.acc` / `.json` package imports validate archive entry counts, total uncompressed size, canonical paths (Zip-Slip protection), and explicitly reject `.apk` files.
- `FileProvider` exposure is restricted strictly to `cache/exports/`.

## Reporting a Vulnerability
If you discover a security issue (such as an archive parsing bug, intent handling flaw, or data-exposure issue):
1. Please **do not** disclose exploit details in a public GitHub issue before a fix is available.
2. Report it privately using GitHub's **Private Vulnerability Reporting** ("Security" → "Report a vulnerability") on this repository.
3. Include steps to reproduce and the affected app version / Android API level.
