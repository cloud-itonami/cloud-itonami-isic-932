(ns amusementfacilityops.sim
  "Demo driver for the amusement/recreation facility coordination actor.

  Runs a self-contained simulation with demo data through the REAL
  compiled `langgraph.graph` StateGraph
  (`amusementfacilityops.operation/build`) -- not a hand-rolled pipeline.
  Exercises the governor's hard checks, the phase auto-commit gate, and
  the human-in-the-loop escalation/approval `interrupt-before` pause +
  resume."
  (:require [amusementfacilityops.store :as store]
            [amusementfacilityops.operation :as operation]
            [langgraph.graph :as g]))

(defn- exec [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

;; ---------------------- demo scenarios ----------------------

(defn demo-happy-path
  "Scenario 1: Valid booking request from a verified facility, at a phase
  where :schedule-facility-booking auto-commits -> commits through the
  real compiled graph, straight through :decide -> :commit."
  []
  (let [s (store/make-store)
        actor (operation/build s)]
    (println "\n=== Demo 1: Happy Path (Valid Booking, auto-commits at phase 3) ===")
    (println "Facility: Central Amusement Park (verified)")
    (println "Request: Private group event booking")
    (let [result (exec actor "demo-1"
                        {:operation :schedule-facility-booking
                         :facility-id "facility-1"
                         :event-name "Corporate Team Building"
                         :event-date "2026-07-25"
                         :party-size 120}
                        3)]
      (println "Status:" (:status result))
      (println "Decision:" (:decision (:state result)))
      (println "Ledger entries:" (count (store/ledger s)))
      result)))

(defn demo-unverified-facility
  "Scenario 2: Request targets an unverified facility -- a HARD,
  permanent governor check fails. The real graph routes straight to
  :hold (never through human approval, regardless of phase)."
  []
  (let [s (store/make-store)
        actor (operation/build s)]
    (println "\n=== Demo 2: Unverified Facility (Hard Check Fails) ===")
    (println "Facility: Regional Arcade (NOT verified)")
    (println "Request: Maintenance schedule proposal")
    (let [result (exec actor "demo-2"
                        {:operation :coordinate-maintenance-schedule-proposal
                         :facility-id "facility-3"
                         :attraction-id "attraction-5"
                         :maintenance-type "quarterly-inspection"
                         :scheduled-date "2026-08-01"}
                        3)]
      (println "Status:" (:status result))
      (println "Decision:" (:decision (:state result)))
      (println "Violations:" (:violations (:state result)))
      result)))

(defn demo-scope-exclusion
  "Scenario 3: Request touches excluded territory (scope check fails) --
  a HARD, permanent governor block regardless of phase or confidence."
  []
  (let [s (store/make-store)
        actor (operation/build s)]
    (println "\n=== Demo 3: Scope Exclusion (Safety Sign-Off Rejected) ===")
    (println "Facility: Central Amusement Park")
    (println "Request: Attempting to propose ride safety certification")
    (let [result (exec actor "demo-3"
                        {:operation :coordinate-maintenance-schedule-proposal
                         :facility-id "facility-1"
                         :attraction-id "roller-coaster-1"
                         :maintenance-type "safety-inspection-sign-off"
                         :scheduled-date "2026-08-01"}
                        3)]
      (println "Status:" (:status result))
      (println "Decision:" (:decision (:state result)))
      (println "Violations:" (:violations (:state result)))
      result)))

(defn demo-safety-escalation
  "Scenario 4: Safety concern flag -- ALWAYS escalates. The real graph
  genuinely interrupts (checkpointed) at :request-approval; a human
  operator's approval resumes the SAME compiled graph and commits via
  the graph's own :request-approval -> :commit edge."
  []
  (let [s (store/make-store)
        actor (operation/build s)]
    (println "\n=== Demo 4: Safety Concern (Always Escalates, then Approved) ===")
    (println "Facility: Central Amusement Park")
    (println "Request: Equipment malfunction safety concern")
    (let [held (exec actor "demo-4"
                      {:operation :flag-safety-concern
                       :facility-id "facility-1"
                       :concern-type "equipment-malfunction"
                       :description "Carousel motor showing unusual vibration"
                       :severity :high}
                      3)]
      (println "Status (pre-approval):" (:status held))
      (println "Ledger entries (pre-approval, must be 0):" (count (store/ledger s)))
      (let [approved (g/run* actor {:approval {:status :approved :by "ops-manager-01"}}
                              {:thread-id "demo-4" :resume? true})]
        (println "Status (post-approval):" (:status approved))
        (println "Decision (post-approval):" (:decision (:state approved)))
        (println "Ledger entries (post-approval, must be 1):" (count (store/ledger s)))
        approved))))

(defn demo-supply-coordination
  "Scenario 5: Non-safety supply request at phase 0 (nothing auto-commits
  yet) -- clean proposal, but held for review because it isn't in the
  current phase's auto-commit set (distinct from a governor violation)."
  []
  (let [s (store/make-store)
        actor (operation/build s)]
    (println "\n=== Demo 5: Guest Services Supply Request (Phase 0, held for review) ===")
    (println "Facility: Community Recreation Center")
    (println "Request: Consumables for guest operations")
    (let [result (exec actor "demo-5"
                        {:operation :coordinate-supply-request
                         :facility-id "facility-2"
                         :supply-type "guest-beverages"
                         :quantity 500
                         :requested-delivery-date "2026-07-20"}
                        0)]
      (println "Status:" (:status result))
      (println "Decision:" (:decision (:state result)))
      result)))

;; ---------------------- run all demos ----------------------

(defn run-all-demos
  "Execute all demo scenarios."
  []
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║ ISIC-932 Amusement Facility Coordination Actor Demo        ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (demo-happy-path)
  (demo-unverified-facility)
  (demo-scope-exclusion)
  (demo-safety-escalation)
  (demo-supply-coordination)

  (println "\n╔════════════════════════════════════════════════════════════╗")
  (println "║ All demo scenarios completed.                               ║")
  (println "╚════════════════════════════════════════════════════════════╝\n"))

;; ---------------------- entry point ----------------------

#?(:clj
   (defn -main [& args]
     (run-all-demos)))
