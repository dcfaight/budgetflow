# Changelog

All notable prototype-facing changes are documented here.

## [Unreleased]

### Added
- Evaluation runbook: `docs/evaluate.md` with end-to-end commands, observation checkpoints, and interpretation guidance.
- Status/roadmap document: `docs/status-roadmap.md` with explicit prototype maturity framing.
- Public milestone summary doc: `docs/milestone-public-prototype.md` with release-note-style evaluator guidance.
- Scenario metadata fields for real-world pattern mapping and explicit "what to observe" guidance.
- Guided walkthrough entrypoint: `:budgetflow-demo-fintech:runDashboardWalkthrough` for the preferred local demo/evaluation flow.
- Planner customization guide: `docs/planner-customization.md` covering the default path, built-in profiles, and custom selector boundaries.
- Tight-budget/low-pressure scenario for validating path-aware budget rescue without runtime-pressure noise.

### Changed
- Planner internals now separate planning-signal analysis, optional-task policy selection, and reason formatting more explicitly while preserving deterministic trace output.
- Planner reason output now includes explicit decision-layer markers (`layer=...`) to make runtime decision layering easier to interpret in trace/report output.
- Planner optional-task strategy depth:
  - balanced profile now uses mixed-constraint degraded-path preference signals
  - continuity profile now prioritizes fallback paths before approximate when degrading
  - reason strings now include mixed-constraint band, decision-layer, and degraded-path preference markers
- Comparison formatter now prints and exports comparison takeaways plus richer confidence summaries alongside pack-level and scenario-level guidance in text and JSON outputs.
- Comparison formatter now includes evaluation-entry hints, compact reason markers, and per-scenario evaluator next-step guidance.
- README and architecture/usage/quickstart/evaluation docs updated for coherent adoption framing, planner customization guidance, and evaluation flow.
