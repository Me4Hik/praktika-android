# Git workflow — clean public history, free local WIP

This repository uses a three-lane model so Cursor can iterate freely
while GitHub stays portfolio-clean.

## Lanes

### `main`

- The only permanent public development line.
- Only reviewed, meaningful commits.
- No WIP commits.
- No experiments started directly on `main`.

### `work/<task>`

- Local working sandbox for one task.
- Any number of commits; messages may be anything (`wip`, `try2`, `test`, …).
- **Never push** `work/*` to GitHub.
- Keep the branch until the task is safely transferred to `review/*` / `main`.

### `review/<task>`

- Created from current `main`.
- Holds the cleaned result of `work/<task>` (1–3 logical commits).
- May be pushed briefly for a Pull Request, then deleted after merge.

---

## Rule 1 — never edit product code on `main`

Never start source/config changes directly on `main`.

## Rule 2 — leave `main` before a code task

If the current branch is `main` and a new code task begins, create
`work/<task>` **before** changing source.

## Rule 3 — `work/*` never push

```text
work/* NEVER PUSH
```

## Rule 4 — bug fix

```text
main
→ work/fix-...
→ WIP commits (any messages)
→ tests / verification
→ review/fix-... from main
→ git merge --squash work/fix-...
→ one clean commit: fix: ...
→ merge into main
→ delete local work/review when done
```

## Rule 5 — feature

```text
main
→ work/feat-...
→ WIP commits
→ tests / verification
→ review/feat-... from main
→ controlled transfer → 1–3 clean logical commits
  (feat: / refactor: / test: / docs: as needed)
→ merge into main
→ delete local work/review when done
```

## Rule 6 — push commands that are forbidden in normal work

Do **not** use:

```text
git push --all
git push --tags
git push origin work/*
git push origin stitch-redesign
```

Push only explicit refs, for example:

```text
git push -u origin main
git push origin refs/tags/<tag-name>
```

Legacy local experiment branch `stitch-redesign` stays local-only unless
explicitly reviewed through the `work`/`review` process.

## Rule 7 — tester / pre-play release

```text
clean main
→ versionCode bump (separate chore/release commit; always increasing)
→ verified production release build
→ tester distribution package
→ new immutable preplay tag on that main commit
```

## Rule 8 — tags are immutable

Never move or rewrite existing preplay tags (for example `preplay-t001-vc8`).

---

## Public commit messages (`review/*` and `main`)

Use short Conventional-lite prefixes:

```text
fix:
feat:
refactor:
test:
chore:
docs:
```

No release automation is required from these prefixes.

On `work/*`, any commit message is allowed.

---

## Version and tag naming

### Pre-play (tester distribution)

```text
preplay-tNNN-vcN
```

- `tNNN` — distribution sequence (`t001`, `t002`, …), never reused
- `vcN` — Android `versionCode` in the distributed APK

Examples: `preplay-t001-vc8`, `preplay-t002-vc9`.

### Stable releases

```text
vMAJOR.MINOR.PATCH
```

Examples: `v1.0.0`, `v1.0.1`, `v1.1.0`.

Tags are created only on clean `main` after a verified APK build.

---

## Cursor gate (every new code task)

1. Check current branch, `git status`, and `main` HEAD.
2. If on `main` and the task changes source/config → create/switch to `work/<task>` first.
3. Never push `work/*`.
4. Never rewrite public `main` history after it has been pushed.
5. Transfer to `main` only through `review/<task>` (squash for bugs; 1–3 commits for features).
