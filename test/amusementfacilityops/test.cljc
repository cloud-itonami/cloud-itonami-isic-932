(ns amusementfacilityops.test
  "Test suite for the amusement facility coordination actor.

  Tests cover the three HARD governor checks and the happy path proposal flow."
  (:require [amusementfacilityops.store :as store]
            [amusementfacilityops.governor :as governor]
            [amusementfacilityops.operation :as operation]
            [amusementfacilityops.phase :as phase]
            #?@(:clj [[clojure.test :refer [deftest is testing]]]))
  #?(:cljs
     (:require-macros [cljs.test :refer [deftest is testing]])))

;; ---------------------- test utilities ----------------------

(defn run-test-group [name f]
  (println (str "\n" name))
  (f))

;; ---------------------- store tests ----------------------

(defn test-store []
  (run-test-group "=== Store Tests ===" #(
    (let [s (store/make-store)]
      (println "[1] Facility lookup")
      (assert (= "Central Amusement Park" (:name (store/facility s "facility-1"))))
      (println "    ✓ facility-1 found and verified")

      (println "[2] All facilities")
      (let [all (store/all-facilities s)]
        (assert (= 3 (count all)))
        (println "    ✓ 3 facilities in store"))

      (println "[3] Booking lookup")
      (assert (= "Private Group Event" (:event-name (store/booking s "booking-1"))))
      (println "    ✓ booking-1 found")

      (println "[4] Ledger append")
      (store/append-ledger! s {:event "test-fact"})
      (assert (= 1 (count (store/ledger s))))
      (println "    ✓ ledger append works")))))

;; ---------------------- governor tests ----------------------

(defn test-governor []
  (run-test-group "=== Governor Tests ===" #(
    (let [s (store/make-store)]
      (println "[1] Facility unverified check")
      (let [violations (governor/facility-unverified-violations s "facility-3")]
        (assert (= 1 (count violations)))
        (assert (= :facility-unverified (get-in violations [0 :check/id])))
        (println "    ✓ unverified facility blocked"))

      (println "[2] Effect not :propose check")
      (let [proposal {:operation :schedule-facility-booking :effect :commit :facility-id "facility-1"}
            violations (governor/effect-not-propose-violations proposal)]
        (assert (= 1 (count violations)))
        (println "    ✓ non-:propose effect blocked"))

      (println "[3] Scope exclusion (safety sign-off)")
      (let [proposal {:operation :coordinate-maintenance-schedule-proposal
                      :facility-id "facility-1"
                      :maintenance-type "safety-inspection-sign-off"
                      :effect :propose}
            violations (governor/scope-exclusion-violations proposal)]
        (assert (= 1 (count violations)))
        (println "    ✓ safety sign-off blocked"))

      (println "[4] Scope exclusion (pricing policy)")
      (let [proposal {:operation :coordinate-supply-request
                      :facility-id "facility-1"
                      :description "update pricing policy"
                      :effect :propose}
            violations (governor/scope-exclusion-violations proposal)]
        (assert (= 1 (count violations)))
        (println "    ✓ pricing policy blocked"))

      (println "[5] Flag-safety-concern allowed (legitimate use)")
      (let [proposal {:operation :flag-safety-concern
                      :facility-id "facility-1"
                      :concern-type "safety-issue"
                      :effect :propose}
            violations (governor/scope-exclusion-violations proposal)]
        (assert (= 0 (count violations)))
        (println "    ✓ flag-safety-concern not auto-blocked"))

      (println "[6] Full governor decision (pass)")
      (let [proposal {:operation :schedule-facility-booking
                      :facility-id "facility-1"
                      :effect :propose}
            result (governor/govern s proposal)]
        (assert (:passes? result))
        (assert (= :APPROVE (:decision result)))
        (println "    ✓ governance passes for verified facility")))))

;; ---------------------- operation tests ----------------------

(defn test-operations []
  (run-test-group "=== Operation Tests ===" #(
    (let [s (store/make-store)]
      (println "[1] Booking proposal (happy path)")
      (let [result (operation/run-proposal s
                     {:operation :schedule-facility-booking
                      :facility-id "facility-1"
                      :event-name "Test Event"
                      :event-date "2026-07-30"
                      :party-size 100})]
        (assert (= :request-approval (:action result)))
        (println "    ✓ booking proposal completes"))

      (println "[2] Unverified facility rejection")
      (let [result (operation/run-proposal s
                     {:operation :schedule-facility-booking
                      :facility-id "facility-3"
                      :event-name "Test Event"
                      :event-date "2026-07-30"
                      :party-size 50})]
        (assert (= :hold (:action result)))
        (println "    ✓ unverified facility held"))

      (println "[3] Safety concern escalation")
      (let [result (operation/run-proposal s
                     {:operation :flag-safety-concern
                      :facility-id "facility-1"
                      :concern-type "equipment-issue"
                      :description "Test safety concern"
                      :severity :high})]
        (assert (= :escalated (:action result)))
        (println "    ✓ safety concern escalates"))))))

;; ---------------------- phase tests ----------------------

(defn test-phases []
  (run-test-group "=== Phase Tests ===" #(
    (println "[1] Phase 0 (read-only)")
    (assert (not (phase/auto-commits-at-phase? 0 :schedule-facility-booking)))
    (println "    ✓ no auto-commit at phase 0")

    (println "[2] Phase 1 (booking + maintenance)")
    (assert (phase/auto-commits-at-phase? 1 :schedule-facility-booking))
    (assert (phase/auto-commits-at-phase? 1 :coordinate-maintenance-schedule-proposal))
    (assert (not (phase/auto-commits-at-phase? 1 :coordinate-supply-request)))
    (println "    ✓ booking and maintenance auto-commit at phase 1")

    (println "[3] Phase 3 (full auto-commit)")
    (assert (phase/always-escalates? 3 :flag-safety-concern))
    (println "    ✓ safety concerns escalate at phase 3"))))

;; ---------------------- master test runner ----------------------

(defn run-all-tests []
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║ ISIC-932 Amusement Facility Coordination Actor Tests       ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (test-store)
  (test-governor)
  (test-operations)
  (test-phases)

  (println "\n╔════════════════════════════════════════════════════════════╗")
  (println "║ All tests passed!                                          ║")
  (println "╚════════════════════════════════════════════════════════════╝\n"))

#?(:clj
   (defn -main [& args]
     (run-all-tests)))
