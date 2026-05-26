# BudgetFlow scenario catalog

This page maps the current scenario catalog so you can reason about evaluation coverage
before choosing which packs and scenarios to run.

For pack descriptions and run commands see [evaluate.md](evaluate.md).
For profile interpretation see [interpreting-profiles.md](interpreting-profiles.md).

---

## Taxonomy dimensions

Each scenario is classified along five dimensions:

| Dimension | Values |
|-----------|--------|
| **Endpoint intent** | `dashboard_endpoint`, `agent_coordination`, `agent_profile_review` |
| **Pressure mode** | `control`, `budget_only`, `pressure_only`, `dominant_signal`, `mixed_constraint`, `boundary_joint_stress` |
| **Degradation style** | `baseline_convergence`, `optional_pruning`, `path_aware_budget_rescue`, `fallback_then_prune`, `profile_tradeoff`, `cascade_boundary` |
| **Coordination pattern** | `single_endpoint`, `plan_fanout_consolidate`, `profile_side_by_side` |
| **Scenario type** | `control`, `budget_stress`, `pressure_stress`, `profile_review`, `boundary_case`, `mixed_stress` |

---

## Scenarios grouped by endpoint intent

### `dashboard_endpoint` — fintech account dashboard

These scenarios target the five-task dashboard endpoint
(`balance`, `transactions`, `rewards`, `offers`, `insights`) with varying budget and pressure settings.

| Scenario | Pack(s) | Pressure mode | Degradation style |
|----------|---------|---------------|-------------------|
| `generous_budget_low_pressure` | default, extended, realism, adoption | control | baseline_convergence |
| `constrained_budget_low_pressure` | default, extended, realism, policy | budget_only | optional_pruning |
| `constrained_budget_elevated_pressure` | default, extended, realism, policy | mixed_constraint | fallback_then_prune |
| `tight_budget_low_pressure` | extended, realism | budget_only | path_aware_budget_rescue |
| `generous_budget_elevated_pressure` | extended | pressure_only | fallback_then_prune |
| `tight_budget_moderate_db_pressure` | extended, realism, policy, adoption | dominant_signal | fallback_then_prune |
| `moderate_budget_downstream_spike` | extended, realism | dominant_signal | fallback_then_prune |
| `moderate_budget_elevated_pressure` | policy, agent | mixed_constraint | profile_tradeoff |
| `commuter_spike_mixed_pressure` | adoption | mixed_constraint | fallback_then_prune |

### `agent_coordination` — multi-step agent turn

These scenarios target the coordination-oriented agent flow
(plan → two parallel sub-agent fetches → consolidate → polish).

| Scenario | Pack | Pressure mode | Degradation style |
|----------|------|---------------|-------------------|
| `agent_coordination_healthy` | agent | control | baseline_convergence |
| `agent_coordination_degraded_cascade` | agent | boundary_joint_stress | cascade_boundary |

### `agent_profile_review` — four-way profile comparison

| Scenario | Pack | Pressure mode | Degradation style |
|----------|------|---------------|-------------------|
| `agent_profile_comparison` | agent, policy | mixed_constraint | profile_tradeoff |

---

## Scenarios grouped by pressure mode

### `control` — healthy system, comfortable budget
Use to verify no unnecessary degradation occurs under baseline conditions.

- `generous_budget_low_pressure`
- `agent_coordination_healthy`

### `budget_only` — budget is the binding constraint; system is healthy
Use to validate budget-semantics without pressure noise.

- `constrained_budget_low_pressure`
- `tight_budget_low_pressure`

### `pressure_only` — system pressure with generous budget
Use to isolate runtime-pressure signal behavior.

- `generous_budget_elevated_pressure`

### `dominant_signal` — one pressure dimension drives decisions
Use to check that single-dimension pressure (DB queue, downstream service) is reflected in trace reasons.

- `tight_budget_moderate_db_pressure`
- `moderate_budget_downstream_spike`

### `mixed_constraint` — budget and multiple pressure dimensions are both elevated
Use for realistic multi-signal stress validation.

- `constrained_budget_elevated_pressure`
- `moderate_budget_elevated_pressure`
- `commuter_spike_mixed_pressure`
- `agent_profile_comparison`

### `boundary_joint_stress` — severe joint budget + pressure; boundary/cascade case
Use only to verify deterministic cascade behavior. Not a production-typical scenario.

- `agent_coordination_degraded_cascade`

---

## Scenarios grouped by degradation style

### `baseline_convergence` — adaptive and naive should produce the same plan
Expected outcome: no meaningful difference between strategies under comfortable conditions.

- `generous_budget_low_pressure`
- `agent_coordination_healthy`

### `optional_pruning` — optional work is the primary casualty
Expected outcome: mandatory and important tasks stay stable; optional tasks are first to degrade or be omitted.

- `constrained_budget_low_pressure`

### `path_aware_budget_rescue` — degraded-path latency hints matter even without runtime pressure
Expected outcome: fallback/approximate choices reduce projected work and preserve more total coverage than primary-path-only reasoning would allow.

- `tight_budget_low_pressure`

### `fallback_then_prune` — important fallback then optional omission
Expected outcome: important tasks fall back before important tasks are omitted; optional tasks are pruned when budget or pressure is severe.

- `constrained_budget_elevated_pressure`
- `generous_budget_elevated_pressure`
- `tight_budget_moderate_db_pressure`
- `moderate_budget_downstream_spike`
- `commuter_spike_mixed_pressure`

### `profile_tradeoff` — profiles diverge clearly; intended for profile comparison
Expected outcome: profiles differ in optional coverage, fallback usage, and headroom in ways that are explainable from profile intent.

- `moderate_budget_elevated_pressure`
- `agent_profile_comparison`

### `cascade_boundary` — severe stress causes full coordination cascade
Expected outcome: all important coordination steps fall back simultaneously; mandatory plan step still executes; cascade is correct, expected behavior.

- `agent_coordination_degraded_cascade`

---

## Pack coverage at a glance

| Pack | Scenarios | Primary purpose |
|------|-----------|-----------------|
| `default` | generous/constrained-low, constrained-elevated | First-run baseline: control + core constrained scenarios |
| `extended` | all 7 dashboard scenarios | Broader pressure variety including path-aware and dominant-signal |
| `realism` | 5 scenarios with recognizable real-world patterns | Pressure narrative variety; good for JSON artifact sharing |
| `policy` | 4 scenarios designed for profile divergence | Profile selection: balanced vs continuity vs efficiency |
| `adoption` | control + commuter spike + DB bottleneck | Compact realistic storyline for first-pass adoption eval |
| `agent` | 4 agent scenarios + profile comparison | Agent coordination, cascade boundary, four-way profile review |

**Suggested progression:**

1. `default` — establish the control case and core constrained behavior
2. `adoption` — validate a compact realistic storyline
3. `realism` — broader pressure coverage, good for sharing artifacts
4. `policy` — deliberate profile comparison when choosing a profile
5. `agent` — agent-step coordination and cascade boundary cases; use `--policies=balanced,continuity,efficiency,latency_first`

---

## Coverage gaps (known)

The current catalog does not include scenarios for:

- **Continuity-sensitive workflows** (multi-step pipelines where partial state matters across turns)
- **Budget-sensitive high-frequency endpoints** (rapid repeated short requests)
- **Warm-cache vs cold-cache pressure splits** (latency hint accuracy under mixed-freshness conditions)

These are noted as potential additions for a future catalog extension.
Adding new scenarios should follow the existing `PressureScenarios` factory pattern and include
`evaluationFocus`, `interpretationGuidance`, `realWorldPattern`, and `whatToObserve` fields so
the new scenarios integrate with the formatter and evaluator UI automatically.
