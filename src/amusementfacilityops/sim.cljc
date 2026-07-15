(ns amusementfacilityops.sim
  "Demo driver for the amusement/recreation facility coordination actor.

  Runs a self-contained simulation with demo data, exercises the governor's
  hard checks, and shows the state machine flow end-to-end."
  (:require [amusementfacilityops.store :as store]
            [amusementfacilityops.operation :as operation]
            [amusementfacilityops.governor :as governor]))

;; ---------------------- demo scenarios ----------------------

(defn demo-happy-path
  "Scenario 1: Valid booking request from a verified facility."
  []
  (let [s (store/make-store)]
    (println "\n=== Demo 1: Happy Path (Valid Booking) ===")
    (println "Facility: Central Amusement Park (verified)")
    (println "Request: Private group event booking")
    (let [result (operation/run-proposal s
                   {:operation :schedule-facility-booking
                    :facility-id "facility-1"
                    :event-name "Corporate Team Building"
                    :event-date "2026-07-25"
                    :party-size 120})]
      (println "Action:" (:action result))
      (println "Governance passes?:" (get-in result [:governance :passes?]))
      result)))

(defn demo-unverified-facility
  "Scenario 2: Request targets an unverified facility (hard check fails)."
  []
  (let [s (store/make-store)]
    (println "\n=== Demo 2: Unverified Facility (Hard Check Fails) ===")
    (println "Facility: Regional Arcade (NOT verified)")
    (println "Request: Maintenance schedule proposal")
    (let [result (operation/run-proposal s
                   {:operation :coordinate-maintenance-schedule-proposal
                    :facility-id "facility-3"
                    :attraction-id "attraction-5"
                    :maintenance-type "quarterly-inspection"
                    :scheduled-date "2026-08-01"})]
      (println "Action:" (:action result))
      (println "Governance passes?:" (get-in result [:governance :passes?]))
      (println "Violations:" (get-in result [:governance :violations]))
      result)))

(defn demo-scope-exclusion
  "Scenario 3: Request touches excluded territory (scope check fails)."
  []
  (let [s (store/make-store)]
    (println "\n=== Demo 3: Scope Exclusion (Safety Sign-Off Rejected) ===")
    (println "Facility: Central Amusement Park")
    (println "Request: Attempting to propose ride safety certification")
    (let [result (operation/run-proposal s
                   {:operation :coordinate-maintenance-schedule-proposal
                    :facility-id "facility-1"
                    :attraction-id "roller-coaster-1"
                    :maintenance-type "safety-inspection-sign-off"
                    :scheduled-date "2026-08-01"
                    :note "This should trigger scope exclusion check"})]
      (println "Action:" (:action result))
      (println "Governance passes?:" (get-in result [:governance :passes?]))
      (println "Violations:" (get-in result [:governance :violations]))
      result)))

(defn demo-safety-escalation
  "Scenario 4: Safety concern flag (always escalates)."
  []
  (let [s (store/make-store)]
    (println "\n=== Demo 4: Safety Concern (Always Escalates) ===")
    (println "Facility: Central Amusement Park")
    (println "Request: Equipment malfunction safety concern")
    (let [result (operation/run-proposal s
                   {:operation :flag-safety-concern
                    :facility-id "facility-1"
                    :concern-type "equipment-malfunction"
                    :description "Carousel motor showing unusual vibration"
                    :severity :high})]
      (println "Action:" (:action result))
      (println "Escalated?:" (= :escalated (:action result)))
      result)))

(defn demo-supply-coordination
  "Scenario 5: Non-safety supply request (happy path)."
  []
  (let [s (store/make-store)]
    (println "\n=== Demo 5: Guest Services Supply Request ===")
    (println "Facility: Community Recreation Center")
    (println "Request: Consumables for guest operations")
    (let [result (operation/run-proposal s
                   {:operation :coordinate-supply-request
                    :facility-id "facility-2"
                    :supply-type "guest-beverages"
                    :quantity 500
                    :requested-delivery-date "2026-07-20"})]
      (println "Action:" (:action result))
      (println "Governance passes?:" (get-in result [:governance :passes?]))
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
  (println "║ All demo scenarios completed successfully.                 ║")
  (println "╚════════════════════════════════════════════════════════════╝\n"))

;; ---------------------- entry point ----------------------

#?(:clj
   (defn -main [& args]
     (run-all-demos)))
