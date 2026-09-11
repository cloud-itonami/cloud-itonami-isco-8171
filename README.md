# cloud-itonami-isco-8171

Open Occupation Blueprint for **ISCO-08 8171**: Pulp and Papermaking Plant Operators.

This repository designs a forkable OSS business for a pulp-and-papermaking-plant scheduling/logistics coordination service: a pulp-and-papermaking-plant scheduling/logistics coordination robot manages production-run/batch/progress record logging, crew/shift scheduling, safety-concern flagging and administrative/equipment supply order coordination under a governor-gated actor, so the pulp/papermaking plant operator keeps its own operating records instead of renting a closed plant-scheduling SaaS.

**This actor coordinates PULP-AND-PAPERMAKING-PLANT SCHEDULING/LOGISTICS ONLY — it never operates pulp-digestion or paper-forming plant equipment itself and never makes a plant-operation-execution or plant-safety-clearance decision.** Pulp and Papermaking Plant Operators run large-scale pulp digestion and paper-forming plant equipment — significant hazards from chemical pulping agents, high-temperature process equipment, and heavy rotating machinery (paper-forming rollers), comparable in stakes to chemical-processing plants in this catalog. The actor's closed op-allowlist contains no op that directly finalizes a plant-operation-execution decision or a plant-safety-clearance decision, nor overrides a plant safety officer's judgment. Any proposal that attempts any of these is a hard, permanent block, never overridable by human approval, and NEVER auto-commit-eligible under any confidence level.

**Maturity: `:implemented`.** `src/pulpcoord/` implements the
`PulpCoordActor` as a `langgraph.graph/state-graph`
(`pulpcoord.actor`) wired to a `Pulp & Papermaking Plant Scheduling &
Logistics Coordination Advisor` (`pulpcoord.advisor`) and an
independent `PulpCoordGovernor` (`pulpcoord.governor`), following
the itonami actor pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `kbb -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the operator/plant
record must be independently verified/registered before any action;
a referenced operator must be a registered certified plant operator
belonging to that plant; `:effect` must be `:propose` only (no hardware
dispatch, no pulp-digestion/paper-forming-equipment operation); the
closed op-allowlist is enforced (no op in the allowlist finalizes a
plant-operation-execution decision, finalizes a plant-safety-clearance
decision, or overrides plant-safety-officer authority); and any
proposal that attempts to directly finalize a plant-operation-
execution decision, finalize a plant-safety-clearance decision, or
override a plant safety officer's judgment is a hard, **permanent**
block — detected as finalization/execution action phrases (never bare
nouns like "pulp"/"digester"/"chemical"/"paper"/"batch", which are
ordinary vocabulary for this domain and must not false-trip the
guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced process-anomaly, chemical-
exposure or equipment-condition concern, ALWAYS, no exceptions, ever)
and `:coordinate-supply-order` above the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a pulp-and-papermaking-plant scheduling/logistics coordination robot performs production-run/batch/progress record logging, crew/shift-schedule proposals, safety-concern surfacing and administrative/equipment supply order coordination under an actor that proposes
actions and an independent **Pulp & Papermaking Plant Scheduling & Logistics Coordination Governor** that gates them. The governor never
dispatches hardware itself, never operates pulp-digestion or paper-forming plant equipment, never finalizes a plant-operation-execution or plant-safety-clearance decision, and never overrides a plant safety officer's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
plant roster + operator roster + plant schedule
        |
        v
Pulp & Papermaking Plant Scheduling & Logistics Coordination Advisor -> PulpCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a plant-operation-execution decision, finalize a plant-
safety-clearance decision, override a plant safety officer's
judgment, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8171`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
