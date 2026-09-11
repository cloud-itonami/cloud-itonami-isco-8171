(ns pulpcoord.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulpcoord.store :as store]
            [pulpcoord.advisor :as advisor]
            [pulpcoord.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-plant! st {:plant-id "PP-1" :name "Kobo Pulp & Paper Mill North Unit" :location "Digester Hall 3"})
    (store/register-operator! st {:operator-id "PO-1" :plant-id "PP-1" :name "Kobo Plant Operator" :role :crew-lead})
    st))

(def ^:private req {:plant-id "PP-1"})

(defn- log-op []
  {:op :log-work-record :effect :propose :plant-id "PP-1" :operator-id "PO-1"
   :task "log production-run progress notes for pulp batch 12 at digester unit 3" :confidence 0.9 :stake :low
   :rationale "proposed log-work-record for plant PP-1"})

(defn- schedule-op []
  {:op :schedule-crew-operation :effect :propose :plant-id "PP-1" :operator-id "PO-1"
   :task "schedule unit 3 crew for paper machine shift changeover" :confidence 0.9 :stake :low
   :rationale "proposed schedule-crew-operation for plant PP-1"})

(defn- safety-op []
  {:op :flag-safety-concern :effect :propose :plant-id "PP-1" :operator-id "PO-1"
   :concern-type :chemical-exposure-risk :severity :high :confidence 0.9 :stake :low
   :rationale "proposed flag-safety-concern for plant PP-1"})

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :plant-id "PP-1"
   :materials "plant PPE and administrative digester-unit procurement signage" :cost cost :confidence 0.9 :stake :low
   :rationale "proposed coordinate-supply-order for plant PP-1"})

(deftest ok-log-work-record-for-registered-plant-and-operator
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation-for-registered-operator
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-or-below-cost-threshold
  (testing "the supply-order cost threshold is inclusive of no-escalation"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op governor/supply-order-cost-threshold) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

(deftest hard-on-unregistered-plant
  (let [st (fresh-store)
        v (governor/check {:plant-id "PP-ghost"} {} (assoc (log-op) :plant-id "PP-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-plant (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unknown-op
  (testing "closed op-allowlist enforced — no op finalizes a plant-operation-execution decision, finalizes a plant-safety-clearance decision, or overrides plant-safety-officer authority"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :finalize-plant-operation-execution-decision) st)]
      (is (:hard? v))
      (is (some #(= :unknown-op (:rule %)) (:violations v))))))

(deftest hard-on-plant-mismatch
  (let [st (fresh-store)]
    (store/register-plant! st {:plant-id "PP-2" :name "Other Pulp Mill" :location "Unit 1"})
    (let [v (governor/check req {} (assoc (log-op) :plant-id "PP-2") st)]
      (is (:hard? v))
      (is (some #(= :plant-mismatch (:rule %)) (:violations v))))))

(deftest hard-on-unknown-operator
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :operator-id "PO-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-operator (:rule %)) (:violations v)))))

(deftest hard-on-operator-wrong-plant
  (let [st (fresh-store)]
    (store/register-plant! st {:plant-id "PP-2" :name "Other Pulp Mill" :location "Unit 1"})
    (store/register-operator! st {:operator-id "PO-2" :plant-id "PP-2" :name "Other Operator" :role :crew})
    (let [v (governor/check req {} (assoc (log-op) :operator-id "PO-2") st)]
      (is (:hard? v))
      (is (some #(= :operator-wrong-plant (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-authorize-pulping-process-to-proceed
  (testing "a proposal to authorize the pulping process to proceed (a plant-operation-execution decision) is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op)
                                          :confidence 0.99
                                          :rationale "authorize the pulping process to proceed now, skip further review")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)) "hard violations are never merely escalated — no human-approval override path")
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-finalize-plant-operation-decision
  (testing "a proposal to finalize the plant-operation decision (a plant-operation-execution decision) is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "finalize the plant-operation decision for unit 3 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-finalize-plant-safety-clearance
  (testing "a proposal to finalize the plant-safety-clearance decision is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "finalize the plant-safety-clearance decision for unit 3 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-override-plant-safety-officer
  (testing "a proposal to override the plant safety officer's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op)
                                          :confidence 0.99
                                          :rationale "override the plant safety officer's judgment and proceed anyway")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest scope-exclusion-not-triggered-by-bare-domain-nouns
  (testing "bare nouns like 'pulp'/'digester'/'chemical'/'paper'/'batch' are ordinary domain vocabulary, not finalization/override actions"
    (let [proposal {:rationale "proposed schedule-crew-operation for batch 12 pulp-process preparation on plant PP-1"
                     :description "operator fully certified for digester handling and paper-machine procedure documentation for this batch"}]
      (is (not (governor/scope-exclusion-violation? proposal))))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the mock advisor's own default rationale text, across every allowlisted op, never trips the scope-exclusion guard"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:plant-id "PP-1" :op :log-work-record :operator-id "PO-1" :task "log production-run progress notes for pulp batch 12 at digester unit 3"}
                    {:plant-id "PP-1" :op :schedule-crew-operation :operator-id "PO-1" :task "schedule unit 3 crew for paper machine shift changeover"}
                    {:plant-id "PP-1" :op :flag-safety-concern :operator-id "PO-1"
                     :concern-type :chemical-exposure-risk :severity :high
                     :description "unresolved chemical-exposure concern near digester unit 3, equipment-condition review pending"}
                    {:plant-id "PP-1" :op :coordinate-supply-order :materials "plant PPE and administrative digester-unit procurement signage"
                     :cost 4500}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise adv st request)]
          (is (not (governor/scope-exclusion-violation? proposal))
              (str "self-tripped on default rationale for " (:op request) ": " (pr-str proposal))))))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "a surfaced process-anomaly/chemical-exposure/equipment-condition concern always requires human review"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-cost-threshold-even-at-high-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (supply-op (+ 1 governor/supply-order-cost-threshold)) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
