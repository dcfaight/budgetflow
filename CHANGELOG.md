# Changelog

All notable prototype-facing changes are documented here.

## [Unreleased]

### Added
- Evaluation runbook: `docs/evaluate.md` with end-to-end commands, observation checkpoints, and interpretation guidance.
- Status/roadmap document: `docs/status-roadmap.md` with explicit prototype maturity framing.
- Scenario metadata fields for real-world pattern mapping and explicit "what to observe" guidance.

### Changed
- Planner optional-task strategy depth:
  - balanced profile now uses mixed-constraint degraded-path preference signals
  - continuity profile now prioritizes fallback paths before approximate when degrading
  - reason strings now include mixed-constraint band and degraded-path preference markers
- Comparison formatter now prints and exports scenario pattern/observation guidance in text and JSON outputs.
- README and architecture/usage docs updated for coherent release-facing framing and evaluation guidance.
