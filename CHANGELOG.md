# Changelog

All notable prototype-facing changes are documented here.

## [Unreleased]

### Added
- Evaluation runbook: `docs/evaluate.md` with end-to-end commands, observation checkpoints, and interpretation guidance.
- Status/roadmap document: `docs/status-roadmap.md` with explicit prototype maturity framing.
- Scenario metadata fields for real-world pattern mapping and explicit "what to observe" guidance.
- Guided walkthrough entrypoint: `:budgetflow-demo-fintech:runDashboardWalkthrough` for the preferred local demo/evaluation flow.

### Changed
- Planner internals now separate planning-signal analysis, optional-task policy selection, and reason formatting more explicitly while preserving deterministic trace output.
- Planner optional-task strategy depth:
  - balanced profile now uses mixed-constraint degraded-path preference signals
  - continuity profile now prioritizes fallback paths before approximate when degrading
  - reason strings now include mixed-constraint band and degraded-path preference markers
- Comparison formatter now prints and exports pack-level suggested-run guidance alongside scenario pattern/observation guidance in text and JSON outputs.
- README and architecture/usage docs updated for coherent release-facing framing, extension guidance, and evaluation flow.
