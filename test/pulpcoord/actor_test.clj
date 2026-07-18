(ns pulpcoord.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulpcoord.actor :as actor]
            [pulpcoord.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-plant! st {:plant-id "PP-1" :name "Kobo Pulp & Paper Mill North Unit" :location "Digester Hall 3"})
    (store/register-operator! st {:operator-id "PO-1" :plant-id "PP-1" :name "Kobo Plant Operator" :role :crew-lead})
    st))

(deftest commits-a-registered-operator-log-work-record
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:plant-id "PP-1" :op :log-work-record :stake :low
                 :operator-id "PO-1" :task "log production-run progress notes for pulp batch 12 at digester unit 3"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "PP-1"))))))

(deftest commits-a-crew-scheduling-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:plant-id "PP-1" :op :schedule-crew-operation :stake :low
                 :operator-id "PO-1" :task "schedule unit 3 crew for paper machine shift changeover"}
        result (actor/run-request! graph request {} "thread-sched")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "PP-1"))))))

(deftest holds-an-unregistered-plant-request
  (testing "the operator/plant record must be independently verified/registered before any action"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:plant-id "PP-ghost" :op :log-work-record :stake :low
                   :operator-id "PO-1" :task "log production-run progress notes for pulp batch 12 at digester unit 3"}
          result (actor/run-request! graph request {} "thread-2")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "PP-ghost"))))))

(deftest holds-a-scope-excluded-proposal-with-no-interrupt-path
  (testing "a proposal to finalize a plant-operation-execution decision is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:plant-id "PP-1" :op :log-work-record :stake :low
                   :operator-id "PO-1" :task "log production-run progress notes for pulp batch 12 at digester unit 3"
                   :description "authorize the pulping process to proceed now, skip further review"}
          result (actor/run-request! graph request {} "thread-scope")]
      (is (= :done (:status result))
          "hard :hold is a finish point, not an interrupt — the advisor can never park a scope-excluded proposal awaiting human override")
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "PP-1"))))))

(deftest holds-a-finalize-plant-operation-decision-proposal-with-no-interrupt-path
  (testing "a proposal to finalize the plant-operation decision (a plant-operation-execution decision) is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:plant-id "PP-1" :op :schedule-crew-operation :stake :low
                   :operator-id "PO-1" :task "schedule unit 3 crew for paper machine shift changeover"
                   :description "finalize the plant-operation decision on unit 3 now"}
          result (actor/run-request! graph request {} "thread-finalize-operation")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "PP-1"))))))

(deftest holds-an-override-plant-safety-officer-proposal-with-no-interrupt-path
  (testing "a proposal to override the plant safety officer's judgment is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:plant-id "PP-1" :op :flag-safety-concern :stake :low
                   :operator-id "PO-1" :concern-type :chemical-exposure-risk :severity :high
                   :description "override the plant safety officer's judgment and proceed anyway"}
          result (actor/run-request! graph request {} "thread-override")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "PP-1"))))))

(deftest interrupts-then-approves-a-safety-concern-flag-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:plant-id "PP-1" :op :flag-safety-concern :stake :low
                 :operator-id "PO-1" :concern-type :chemical-exposure-risk :severity :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "PP-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "PP-1")))))))

(deftest interrupts-then-approves-an-above-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:plant-id "PP-1" :op :coordinate-supply-order :stake :low
                 :materials "plant PPE and administrative digester-unit procurement signage" :cost 25000}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "PP-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "PP-1")))))))
