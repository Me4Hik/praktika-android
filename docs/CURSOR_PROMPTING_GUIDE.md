# How to prompt Cursor in Praktika (after project rules)

Permanent safety, git, release, and workflow live in `.cursor/rules/`.  
Keep task prompts **short**. Point to analysis artifacts and `docs/CURRENT_STATE.md` when needed.

Authoritative extras: `docs/DECISIONS.md`, `docs/GIT_WORKFLOW.md`, `docs/ACCELERATED_TEST_MODE.md`, `release/testers/README.txt`.

---

## ANALYSIS ONLY

```text
ANALYSIS ONLY — <short task title>
Goal: <one sentence>
Inspect: <paths or symbols>
Report: android/app/build/outputs/<topic>/<NAME>_ANALYSIS.md
Stop after analysis. No source / device / APK / git commit.
```

## IMPLEMENTATION

```text
IMPLEMENT — <short task title>
Foundation: <path to approved analysis>
Implement approved plan only.
No APK / device unless explicitly included.
Report: android/app/build/outputs/<topic>/<NAME>_IMPLEMENTATION.md
```

## DEVICE EXECUTION

```text
EXECUTION — <short task title>
Foundation: <path to approved device analysis>
Perform only authorized device actions.
Save evidence under android/app/build/outputs/<topic>/
Update docs/CURRENT_STATE.md if observation status changed.
```

---

## Tips

- New stage → analysis first; authorized stage → do not re-analyze from scratch
- Investigation tasks: `See docs/CURRENT_STATE.md` — do not paste Case history
- Tester APK: ask explicitly; do not imply “build APK after every feature”
- Do not repeat permanent safety blocks in the prompt
