# BMAD Restart Context

This project has selected the MVC version as the active implementation baseline.

## Source Material Policy

- Active implementation artifacts live in `planning-artifacts/` and `implementation-artifacts/`.
- Legacy Lightweight Hexagonal artifacts are preserved under `archive/hexagonal-version/`.
- Existing restart context in `bmad-restart-context-pack/` may be used only for the product problem, observable workflows, and UX intent.
- Legacy technical structure, framework choices, layering decisions, module boundaries, and integration decisions are historical context only.

## Architecture Policy

- The selected architecture is **Traditional MVC + Service/Repository Layering**.
- Do not reopen the Simple MVC vs Lightweight Hexagonal choice during story implementation.
- Do not blend MVC and Hexagonal package boundaries.
- Do not present a hybrid architecture as the final recommendation.
- Implementation stories must follow the selected MVC style consistently.

## Current Context Pack

Use `bmad-restart-context-pack/` to understand the product problem and UX intent only.
Use `archive/hexagonal-version/` only when historical comparison is explicitly needed.
