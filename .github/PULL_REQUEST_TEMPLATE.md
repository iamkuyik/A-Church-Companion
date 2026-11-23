## Summary
<!-- Briefly describe the single feature or bug fix in this PR. -->

## Regression & Minimal-Change Checklist
- [ ] **Minimal diff:** Only files directly required for this task were modified (no unrelated rewrites or formatting churn).
- [ ] **Feature preservation:** Reviewed [`FEATURES.md`](../FEATURES.md); no existing shipped features were removed or altered unintentionally.
- [ ] **Contract & tests updated:** Added/updated entries in `FEATURES.md`, `tools/verify_features.py`, and unit tests under `app/src/test/` for any new or restored behavior.
- [ ] **Branch hygiene:** Branched from up-to-date `main`; checked open PRs for overlapping changes.
- [ ] **Verified:** `python3 tools/verify_features.py` and `./gradlew testDebugUnitTest` pass.
