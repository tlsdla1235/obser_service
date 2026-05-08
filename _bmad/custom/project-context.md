# BMAD Restart Context

This project is being restarted with a clean architecture baseline.

## Source Material Policy

- Existing artifacts may be used only for the product problem and UX intent.
- Existing architecture decisions are not inherited.
- Treat prior technical structure, framework choices, layering decisions, module boundaries, and integration decisions as non-binding historical context.

## Architecture Policy

- The new architecture must choose exactly one style:
  - Simple MVC
  - Lightweight Hexagonal
- Do not blend advantages from multiple architecture styles.
- Do not present a hybrid architecture as the final recommendation.
- If trade-offs are discussed, they must end in a single selected architecture style.
- Implementation stories must follow the selected style consistently.

## Current Context Pack

Existing restart context is in `bmad-restart-context-pack/`.
Use it to understand the product problem, observable workflows, and UX intent only.
