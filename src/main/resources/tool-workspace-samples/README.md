# Spring AI Playground — Sample Workspace

This directory was seeded on first launch so the built-in **file tools** have
something to read out of the box.

## TODO
- Try `readTextFile` — pass `path = README.md`.
- Try `grepFile` — pass `pattern = TODO|FIXME` and `path = README.md`.
- Try `findFiles` — pass `dir = .` and `glob = *.md`.
- Try `sliceFile` — pass `path = README.md`, `start = 0`, `end = 10`.

## FIXME
Replace this placeholder content with your own notes; nothing here is
automatically recreated unless the workspace is empty.

## Notes
The base directory is controlled by
`spring.ai.playground.tool-studio.fs.base-path` (or the
`TOOL_STUDIO_FS_BASE` environment variable). File tools sandbox-resolve
relative paths against this directory only — `..` traversal and absolute
paths outside the base are rejected.
