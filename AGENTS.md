# Agent & Contributor Guidelines

Follow these rules on every task in this repository so existing features are never lost or accidentally rewritten.

## 1. Minimal, Surgical Changes Only
- Touch **only** the files and lines directly required for the requested task.
- Do **not** rewrite unrelated methods, reformat untouched code, or bundle unrelated cleanups/refactors into a feature or bug-fix PR.
- Keep pull requests small and single-purpose (one feature or fix per PR). Never combine unrelated UI overhauls, asset migrations, and new features into a single mega-commit.

## 2. Preserve Existing Features (`FEATURES.md`)
- Read [`FEATURES.md`](FEATURES.md) **before** modifying any Activity, layout, helper, or manifest entry.
- Never remove, disable, or change any shipped behavior listed in `FEATURES.md` unless the user explicitly asks to remove or change it.
- Whenever you add a new user-facing feature (or restore one from the pending list in `FEATURES.md`):
  1. Document it in `FEATURES.md`.
  2. Add a structural check in [`tools/verify_features.py`](tools/verify_features.py) and/or a unit test in `app/src/test/java/com/trust/acc/`.

## 3. Branch & Release Hygiene
- Before starting new work, inspect open PRs and active branches (`git branch -a`, `gh pr list --state open`) so shipped or in-flight work is never left behind on an unmerged branch.
- Always branch from an up-to-date `main` and merge via Pull Request.
- **Never publish a GitHub Release from an unmerged branch.** Merge the PR into `main` first, verify `versionCode` (strictly increasing) and `versionName` in `app/build.gradle`, and then push a `v<versionName>` tag pointing to a commit on `main`.
- Delete feature branches after merging so stale branches cannot diverge from `main`.

## 4. Required Verification Before Opening a PR
Run both checks and confirm they pass:
```bash
python3 tools/verify_features.py
./gradlew testDebugUnitTest --no-daemon
```
If the local environment does not have the Android SDK installed, run `python3 tools/verify_features.py` locally and verify that GitHub Actions CI passes on the PR before merging.
