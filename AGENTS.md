## Review guidelines

Scope
- One high-level task per PR. If multiple unrelated goals appear, request a split.
- No drive‑by changes (formatting/typos/unrelated fixes). Ask for a separate issue/PR.

Migration guides
- If a PR references a milestone or migration guide, verify compliance with it.
- If compliance is unclear, request the PR description to cite the guide section(s).

Correctness
- Confirm the PR fully resolves any referenced issues (Fixes/Closes).
- Flag behavior changes outside stated scope.
- Ensure tests/build/docs remain consistent with the change.

Commit/history hygiene
- Commits must be reviewable and focused.
- Messages should state what changed and why.
- If history is noisy, request cleanup (rebase/squash).

When to request changes
- Mixed scope
- Guide/milestone not followed
- Partial issue resolution
- Unclear/unsafe behavior changes
- Messy history or unclear commit intent
