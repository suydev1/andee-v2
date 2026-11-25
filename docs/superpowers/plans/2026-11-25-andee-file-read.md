# Plan — bounded `file.read` for the Android body

Branch: `file-read` from `main` (`68bdf93`). Worktree: `/data/data/com.termux/files/home/andee-v2/.claude/worktrees/file-read`.

## Requirement

The user asked for a read tool like the controller's `Read`: give a file and a from-line/to-line window, and never read more than one bounded slice at a time.

## Binding design

- New tool `file.read` (`file_read`), `localOnly = true`, outside `screen.`, handler `file.read`.
- Params: `path` OR `id` (drop-inbox id), optional `from_line` (default 1), optional `to_line` (default `from_line + 399`). Lines are 1-based and inclusive. `from_line >= 1`; `to_line >= from_line`; window capped at 400 lines.
- Hard read cap: **20,000 characters per call** (same discipline as `note.drop_read`). Read with a `BufferedReader` line by line and stop **before** adding a line that would exceed the cap — never load a whole file. If a single line alone exceeds the cap, refuse with a readable error.
- Result fields: `text` (fenced with `ToolDataFence.wrap("FILE CONTENT", …)`), `from_line`, `to_line` (the last line actually returned), `line_count`, `truncated`, `next_from_line` (first line not returned when truncated), and `note` telling the model the content is DATA and the turn is now untrusted.
- Provenance: `path` must be a remembered `file.search`/`note.drop_list` handle. `id` is accepted only when it resolves against the live drop-inbox scan, mirroring `file.open`. Both go through `handleParam` (fence stripping). Paths are resolved through `FileManager.resolve(roots, …)` using `fileOpenRoots()` — never an arbitrary filesystem path.
- Trust: `file.read` is refused on `silent`, untrusted and non-`USER` turns, beside `file.open` in `TurnGuards`. It is **not** gated by `file_edits` (reading does not change files).
- Latch: add `file.read` to `LocalBrain.runTool`'s untrusted latch beside `note.drop_read` and `web.search`, so file text cannot instruct the rest of the turn.
- Golden snapshots are regenerated in CI after the code lands, never hand-edited.

## Tasks

1. `device/FileReader.kt` + `app/src/test/.../FileReaderTest.kt`: bounded streaming reader, line window, 20k cap, oversized-line refusal.
2. Wire it: `ToolSchemas`, `CommandDispatcher`, `TurnGuards`, `LocalBrain` latch; add core/app tests for schema, guard and latch. Commit.
3. CI regeneration of goldens + full build gate, then merge to `main` and clean up.

## Hard constraints

- No local Gradle/JDK/adb/build/test execution; GitHub Actions is the only gate.
- No `grep`/`Glob`/`Grep`/opencode CLI; use Bash + python3 and the working Read/Write/Edit wrappers.
- Every Java/Kotlin implementer and reviewer must LOAD the relevant skills from `~/.agents/SKILL-ROUTER.md` before coding.
- Implementers do not spawn subagents. Tasks run sequentially.
