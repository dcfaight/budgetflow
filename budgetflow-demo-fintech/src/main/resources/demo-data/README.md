# BudgetFlow demo fintech dataset pack

This folder contains **fully synthetic, sanitized demo datasets** for the `budgetflow-demo-fintech` app.
No records in this folder come from production systems, and no real identities or account identifiers are used.

## Folder structure

```text
demo-data/
  seed/
    default/
      customers.json
      accounts.json
      transactions.json
      budgets.json
  scenarios/
    stable-monthly-income/
    irregular-gig-income/
    high-subscription-load/
    overspending-user/
    low-cash-buffer/
    many-small-card-transactions/
    paycheck-to-paycheck-user/
      customers.json
      accounts.json
      transactions.json
      budgets.json
      scenario-metadata.json
```

## Dataset purpose

- `seed/default`: deterministic baseline used by default app startup and smoke tests.
- `scenarios/*`: evaluator archetypes for reviewing policy behavior with realistic production-shaped fake data.
- `scenario-metadata.json` (scenario folders): intent + expected evaluator behavior for easier review.

## Schema assumptions

Each dataset directory uses these files:

- `customers.json`: array of customer rows with fake IDs and profile context.
- `accounts.json`: array of account rows with balances and account metadata.
- `transactions.json`: array of posted transaction rows (`postedAt` uses ISO-8601 offset timestamps).
- `budgets.json`: array of simple monthly category budget rows.
- `scenario-metadata.json` (scenario datasets): scenario-level context for evaluator interpretation.

The app currently reads account balances and transactions from the selected dataset while preserving existing adaptive execution demo behavior.

## How the app loads/switches datasets

`DemoDatasetCatalog` loads resources from:

```text
classpath:demo-data/<dataset-id>/
```

Default dataset id:

```text
seed/default
```

Switch datasets with a startup property:

```bash
./gradlew :budgetflow-demo-fintech:bootRun --args="--budgetflow.demo.dataset=scenarios/overspending-user"
```

Equivalent environment variable:

```bash
export BUDGETFLOW_DEMO_DATASET=scenarios/overspending-user
./gradlew :budgetflow-demo-fintech:bootRun
```

Evaluator UI runtime selector (no restart required):

```text
http://localhost:8080/dashboard/evaluator?dataset=scenarios/overspending-user
```

Use `compareDatasets=<dataset-a>,<dataset-b>` for quick side-by-side comparison in the scenario lab table.

## Extending with new scenarios

1. Create `demo-data/scenarios/<new-scenario>/`.
2. Add `customers.json`, `accounts.json`, `transactions.json`, `budgets.json`.
3. Add `scenario-metadata.json` with:
   - `scenarioId`
   - `displayName`
   - `intent`
   - `expectedEvaluatorBehavior`
   - `realWorldPattern`
   - `sanitizedNotice`
4. Run tests for `budgetflow-demo-fintech` to confirm the dataset loads cleanly.
