# CURRENT_STATE — volatile handoff

**Volatile.** Update when tester build, active investigation, or device observation constraints change.  
**Not** an always-on Cursor rule. Case1/Case2 deep timelines live in forensics reports — link, do not paste here.

**Last updated:** 2026-09-03

---

## Tester pointer

Canonical outgoing APK: `release/testers/CURRENT/`  
Read live facts from: `release/testers/CURRENT/BUILD_INFO.txt`  
(Do not treat numbers below as rules — verify BUILD_INFO if critical.)

| Field | Snapshot (2026-09-03) |
|-------|------------------------|
| Tester sequence | t004 |
| versionCode | 11 |
| Notes (BUILD_INFO) | Case1 BOOT/system-event recovery fix; Case2 observability hooks present; **does not claim Case2 fixed** |

---

## Active investigations

### Case2 — long-run silent stop (OPEN)

- Symptom class: notifications stop after long idle / week-like window; resume often on app open
- Observation: production + Auto Start **ON** (keep ON while observing; do not toggle casually)
- Deep reads (only if this task is about Case2):  
  `android/app/build/outputs/sentry-notification-forensics/SUMMARY.md`  
  `CASE2_RESUME_AFTER_AUTOSTART_PASS_ANALYSIS.md`  
  `CASE2_T004_NATURAL_OBSERVATION_PROTOCOL.md`

### Case1 — reboot miss until open (CLOSED for “BOOT broken forever”)

- t004 notes include BOOT/system-event recovery fix
- Do **not** treat pre-t004 “BOOT filter broken / no headless boot” as current permanent architecture truth
- Historical forensics remain under `sentry-notification-forensics/` for reference only

---

## Do not assume (stale)

- Case1 root-cause still unfixed in shipped t004
- Current HEAD / dirty tree / branch name without checking `git status`
- Giant Case corpora needed for unrelated UI/domain features

---

## How to use

Task needs investigation context → `See docs/CURRENT_STATE.md` (+ 1–2 linked reports).  
Unrelated feature work → ignore this file.
