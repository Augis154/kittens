#!/usr/bin/env bash
# Stop hook: when a session leaves Java/Gradle sources changed but CLAUDE.md untouched,
# remind once that the shared project-context file may need updating.
#
# Deliberately NON-blocking. Blocking the stop would either loop forever (the sources stay
# dirty after CLAUDE.md is written, so the condition never clears) or force a pointless doc
# edit for routine changes that CLAUDE.md should not record anyway.
set -uo pipefail

payload=$(cat 2>/dev/null || true)

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0

# Nag at most once per session. There is no jq on this machine, so pull the id out with sed.
session=$(printf '%s' "$payload" \
  | sed -n 's/.*"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  | head -1)
[ -n "$session" ] || session="nosession"
safe=$(printf '%s' "$session" | tr -c 'A-Za-z0-9_-' '_')
sentinel="${TMPDIR:-/tmp}/claude-md-reminder-${safe}"
[ -e "$sentinel" ] && exit 0

# Git pathspec globs match at any depth, so these cover the whole source tree.
sources=$(git status --porcelain -- '*.java' '*.kts' 2>/dev/null | grep -c . || true)
[ "${sources:-0}" -gt 0 ] || exit 0

# CLAUDE.md already has pending edits: whoever touched the code has it covered.
docs=$(git status --porcelain -- CLAUDE.md 2>/dev/null | grep -c . || true)
[ "${docs:-0}" -gt 0 ] && exit 0

: > "$sentinel" 2>/dev/null || true
printf '{"systemMessage":"CLAUDE.md is untouched but %s Java/Gradle file(s) changed. If this altered the architecture, an invariant, the wire protocol, the tick pipeline, or a listed rough edge, update CLAUDE.md — see its \\"Keeping this file current\\" section. Routine changes need no update."}\n' "$sources"
