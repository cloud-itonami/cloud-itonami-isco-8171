(ns pulpcoord.governor
  "PulpCoordGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every pulp-
  and-papermaking-plant scheduling/logistics coordination proposal an
  advisor may make for a pulp and papermaking plant under
  coordination. The governor never dispatches hardware itself, never
  operates pulp-digestion or paper-forming plant equipment, and never
  allows a proposal to finalize a plant-operation-execution decision,
  finalize a plant-safety-clearance decision, or override a plant
  safety officer's judgment — this actor coordinates PULP-AND-
  PAPERMAKING-PLANT SCHEDULING/LOGISTICS ONLY. Modeled on
  cloud-itonami-isco-8131's chemcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. plant provenance        — the operator/plant record must be
                                 independently verified/registered
                                 before any action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never operates pulp-
                                 digestion or paper-forming plant
                                 equipment; it only gates what the
                                 advisor may coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops (:log-work-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op that
                                 directly finalizes a plant-operation-
                                 execution decision, finalizes a
                                 plant-safety-clearance decision, or
                                 overrides plant-safety-officer
                                 authority exists in this allowlist —
                                 these decision classes are
                                 structurally absent, not merely
                                 gated. This actor never operates
                                 pulp-digestion or paper-forming plant
                                 equipment itself; pulping-chemical
                                 handling is entirely out of scope for
                                 this administrative-coordination
                                 actor (:coordinate-supply-order
                                 covers plant-equipment/administrative
                                 -supply procurement only, never
                                 pulping chemicals themselves).
    4. plant-mismatch          — if the proposal names a plant, it
                                 must be the SAME plant verified for
                                 this request (defense-in-depth against
                                 a proposal quietly targeting a
                                 different, unverified plant).
    5. operator basis          — if the proposal references an
                                 operator, that operator must be a
                                 REGISTERED certified pulp-and-
                                 papermaking-plant operator belonging
                                 to this plant (an unregistered or
                                 foreign-plant operator reference is
                                 not a routine scheduling proposal).
    6. scope-exclusion         — a proposal that attempts to finalize
                                 a plant-operation-execution decision,
                                 to finalize a plant-safety-clearance
                                 decision, or to override a plant
                                 safety officer's judgment, is a hard,
                                 PERMANENT block — never overridable
                                 by human approval, regardless of
                                 confidence or stake, and NEVER
                                 auto-commit-eligible under any
                                 confidence level. Detected as
                                 finalization/execution ACTION PHRASES
                                 (e.g. 'authorize the pulping process
                                 to proceed', 'finalize the plant-
                                 operation decision', 'override the
                                 plant safety officer's judgment') in
                                 free-text proposal fields, never as
                                 bare domain nouns ('pulp', 'digester',
                                 'chemical', 'paper', 'batch') — bare-
                                 noun matching would false-trip on the
                                 default mock advisor's own routine
                                 rationale text, since this actor's
                                 entire domain is pulp-and-papermaking-
                                 plant scheduling coordination. See
                                 `pulpcoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced process-
                                 anomaly, chemical-exposure or
                                 equipment-condition concern ALWAYS
                                 requires human review — the governor
                                 never resolves a safety concern
                                 itself, and this is unconditional —
                                 no confidence-level exception, ever).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [pulpcoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("pulp", "digester", "chemical",
;; "paper", "batch") — this actor's entire domain is pulp-and-
;; papermaking-plant scheduling coordination, so bare-noun matching
;; would false-trip on the default mock advisor's own routine
;; rationale text (e.g. "proposed :coordinate-supply-order for plant
;; PP-1" naming plant equipment and consumable signage, or a
;; crew-schedule proposal naming a batch/process under preparation).
;; See governor-test's dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["authorize the pulping process to proceed"
   "authorize the digester to proceed"
   "approve the pulping process to proceed"
   "approve the digester run to proceed"
   "clear the digester for operation"
   "clear the plant for the pulping process"
   "clear the plant for the digester run"
   "finalize the plant-operation decision"
   "finalize the plant-operation-execution decision"
   "finalize the digester-operation decision"
   "finalize the pulping-process operation"
   "finalize the pulping process operation"
   "finalize the plant-operation execution"
   "finalize the plant-safety-clearance decision"
   "finalize the plant safety clearance"
   "finalize the plant-safety clearance decision"
   "execute the digester run directly"
   "execute the pulping process directly"
   "perform the digester run directly"
   "perform the pulping process directly"
   "initiate the digester run directly"
   "initiate the pulping process directly"
   "trigger the digester run directly"
   "start the digester directly"
   "start the pulping process directly"
   "start the paper machine directly"
   "dispatch the crew to run the digester"
   "dispatch the operator to start the paper machine"
   "sign off the plant-operation authorization"
   "sign the plant-operation authorization"
   "issue the plant-operation authorization"
   "issue the plant-safety clearance"
   "issue the plant safety clearance"
   "override the plant safety officer's judgment"
   "override the plant safety officer"
   "bypass the plant safety officer"
   "bypass the safety clearance"
   "bypass the plant-operation authorization"])

(defn- scope-excluded-text [proposal]
  (str/lower (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize a
  plant-operation-execution decision, finalize a plant-safety-
  clearance decision, or override plant-safety-officer authority.
  Phrased as multi-word action phrases (never bare nouns) so this
  never false-trips on legitimate pulp-and-papermaking-plant-
  scheduling-coordination domain vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} plant-record o]
  (let [{:keys [op plant-id operator-id]} proposal]
    (cond-> []
      (nil? plant-record)
      (conj {:rule :no-plant :detail "未登録 plant/pulp-papermaking record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は plant 判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（plant-operation-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる op は許可されていない）"})

      (and plant-id (not= plant-id (:plant-id request)))
      (conj {:rule :plant-mismatch :detail "proposal の plant が request で検証済みの plant と一致しない"})

      (and operator-id (nil? o))
      (conj {:rule :unknown-operator :detail "未登録 operator への提案は不可"})

      (and o (not= (:plant-id o) (:plant-id request)))
      (conj {:rule :operator-wrong-plant :detail "operator が別 plant 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "plant-operation-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `pulpcoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never operates pulp-
  digestion or paper-forming plant equipment."
  [request _context proposal store]
  (let [plant-record (store/plant store (:plant-id request))
        o (some->> (:operator-id proposal) (store/operator store))
        hard (hard-violations {:request request :proposal proposal} plant-record o)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
