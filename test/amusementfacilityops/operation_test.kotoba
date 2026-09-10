(ns amusementfacilityops.operation-test
  "Integration tests for `amusementfacilityops.operation/build` -- builds
  the REAL compiled `langgraph.graph` StateGraph and runs it end-to-end
  via `langgraph.graph/run*` through commit / hard-hold / phase-hold /
  escalate-approve / escalate-reject routes.

  This namespace is genuinely falsifiable: every `is` here would fail if
  the graph regressed to the old hand-rolled pipeline behavior (e.g. if
  `:decide` could never reach `:commit` again, or if a HARD governor
  violation were ever routed through human approval instead of straight
  to `:hold`, or if the ledger were written before a commit/hold actually
  happened)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [amusementfacilityops.operation :as operation]
            [amusementfacilityops.store :as store]))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, verified-facility booking request commits
            through the real compiled graph, appends to the audit
            ledger, AND to the coordination-log via store/commit-record!"
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:operation :schedule-facility-booking
                        :facility-id "facility-1"
                        :event-name "Corporate Offsite"
                        :event-date "2026-08-10"
                        :party-size 80}
                       3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:status (first ledger))))
        (is (= :schedule-facility-booking (:operation (first ledger))))
        (is (= "facility-1" (:facility-id (first ledger)))))
      (is (= 1 (count (store/coordination-log s)))
          "store/commit-record! (coordination-log write) fires on the graph's :commit node"))))

(deftest hard-hold-path-unverified-facility
  (testing "an unverified facility is a HARD, permanent governor
            violation -- the graph routes straight to :hold (never
            through human approval, regardless of phase) and durably
            records the hold fact. Governor rejection blocks commit."
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-hold-unverified"
                       {:operation :coordinate-maintenance-schedule-proposal
                        :facility-id "facility-3"
                        :attraction-id "attraction-5"
                        :maintenance-type "quarterly-inspection"
                        :scheduled-date "2026-08-01"}
                       3)
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :held (:status (first ledger))))
        (is (= :governor-violation (:reason (first ledger))))
        (is (seq (:violations (first ledger)))))
      (is (empty? (store/coordination-log s))
          "a held proposal never reaches store/commit-record! -- governor rejection blocks commit"))))

(deftest hard-hold-path-unregistered-facility
  (testing "a facility that doesn't exist in the store at all is also a
            HARD violation, re-derived from the store, never trusted
            from the proposal"
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-hold-unregistered"
                       {:operation :schedule-facility-booking
                        :facility-id "no-such-facility"
                        :event-name "E" :event-date "2026-08-01" :party-size 10}
                       3)]
      (is (= :hold (:decision (:state result))))
      (is (empty? (store/coordination-log s))))))

(deftest hard-hold-path-scope-exclusion
  (testing "a proposal touching ride-safety-inspection sign-off content
            is a HARD scope-exclusion violation, blocked regardless of
            phase or confidence"
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-hold-scope"
                       {:operation :coordinate-maintenance-schedule-proposal
                        :facility-id "facility-1"
                        :attraction-id "roller-coaster-1"
                        :maintenance-type "safety-inspection-sign-off"
                        :scheduled-date "2026-08-01"}
                       3)
          state (:state result)]
      (is (= :hold (:decision state)))
      (is (some #{:scope-exclusion} (map :check/id (:violations state))))
      (is (empty? (store/coordination-log s))))))

(deftest phase-hold-path-clean-but-not-eligible
  (testing "a clean, non-escalating proposal that isn't in the current
            phase's auto-commit set is held (not committed, not
            escalated) -- and STILL durably audited, distinguished from
            a governor violation by :reason. This is the previously-dead
            wiring between amusementfacilityops.phase and :decide."
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-phase-hold"
                       {:operation :coordinate-supply-request
                        :facility-id "facility-2"
                        :supply-type "beverages" :quantity 100
                        :requested-delivery-date "2026-08-01"}
                       0)
          state (:state result)]
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :held (:status (first ledger))))
        (is (= :not-in-phase-auto-set (:reason (first ledger))))
        (is (empty? (:violations (first ledger))))))))

(deftest phase-eligibility-actually-gates-commit
  (testing "the SAME clean proposal that holds at phase 0 auto-commits
            once its op enters the phase's auto-commit set -- proof the
            phase table is a real gate now, not inert decoration"
    (let [held-store (store/make-store)
          held (exec (operation/build held-store) "t-phase-0"
                     {:operation :coordinate-supply-request
                      :facility-id "facility-2" :supply-type "beverages"
                      :quantity 100 :requested-delivery-date "2026-08-01"}
                     0)
          committed-store (store/make-store)
          committed (exec (operation/build committed-store) "t-phase-2"
                          {:operation :coordinate-supply-request
                           :facility-id "facility-2" :supply-type "beverages"
                           :quantity 100 :requested-delivery-date "2026-08-01"}
                          2)]
      (is (= :hold (:decision (:state held))))
      (is (= :commit (:decision (:state committed)))))))

(deftest escalate-then-approve-commits
  (testing ":flag-safety-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval; the
            ledger stays empty until a human approves; approval resumes
            the SAME compiled graph and commits via the graph's own
            :request-approval -> :commit edge, durably appending to the
            ledger AND the coordination-log (hold-until-approved)"
    (let [s (store/make-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:operation :flag-safety-concern
                      :facility-id "facility-1"
                      :concern-type "equipment-malfunction"
                      :description "Carousel motor vibration"
                      :severity :high}
                     3)]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- ledger stays empty until approval")
      (is (empty? (store/coordination-log s)))
      (let [approved (g/run* actor {:approval {:status :approved :by "ops-manager-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:decision approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:status (first ledger))))
          (is (= :flag-safety-concern (:operation (first ledger))))
          (is (= "ops-manager-01" (:approved-by (first ledger)))))
        (is (= 1 (count (store/coordination-log s))))))))

(deftest escalate-then-reject-holds
  (testing "a human operator rejecting an escalated request routes to
            :hold via the :request-approval node's own decision, and
            durably records the rejection -- never commits"
    (let [s (store/make-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:operation :flag-safety-concern
                       :facility-id "facility-1"
                       :concern-type "equipment-malfunction"
                       :description "Tire pressure warning"
                       :severity :medium}
                      3)
          rejected (g/run* actor {:approval {:status :rejected :by "ops-manager-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:status (first ledger)))))
      (is (empty? (store/coordination-log s))))))

(deftest booking-does-not-escalate-and-does-not-auto-commit-at-phase-0
  (testing "a routine, non-safety op at phase 0 is neither escalated
            (no :escalate? flag, not :flag-safety-concern) nor committed
            -- it's simply held for review"
    (let [s (store/make-store)
          actor (operation/build s)
          result (exec actor "t-booking-phase-0"
                       {:operation :schedule-facility-booking
                        :facility-id "facility-1"
                        :event-name "E" :event-date "2026-08-01" :party-size 10}
                       0)]
      (is (= :done (:status result)) "no interrupt -- this op never escalates")
      (is (= :hold (:decision (:state result)))))))
