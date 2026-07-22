(ns amusementfacilityops.phase-test
  "Tests for the 0->3 phase rollout auto-commit table. These fns are now
  genuinely consulted by `amusementfacilityops.operation`'s `:decide`
  node (see `operation-test`) -- previously they were defined and tested
  in isolation but never wired into any decision."
  (:require [clojure.test :refer [deftest is testing]]
            [amusementfacilityops.phase :as phase]))

(deftest phase-0-all-held
  (testing "Phase 0 (read-only): nothing auto-commits"
    (doseq [op [:schedule-facility-booking :coordinate-maintenance-schedule-proposal
                :coordinate-supply-request :coordinate-guest-services-logistics
                :flag-safety-concern]]
      (is (false? (phase/auto-commits-at-phase? 0 op))))))

(deftest phase-1-booking-and-maintenance-only
  (testing "Phase 1: only booking + maintenance auto-commit"
    (is (true? (phase/auto-commits-at-phase? 1 :schedule-facility-booking)))
    (is (true? (phase/auto-commits-at-phase? 1 :coordinate-maintenance-schedule-proposal)))
    (is (false? (phase/auto-commits-at-phase? 1 :coordinate-supply-request)))
    (is (false? (phase/auto-commits-at-phase? 1 :coordinate-guest-services-logistics)))
    (is (false? (phase/auto-commits-at-phase? 1 :flag-safety-concern)))))

(deftest phase-2-adds-supply-and-guest-services
  (testing "Phase 2: booking/maintenance + supply + guest-services auto-commit"
    (doseq [op [:schedule-facility-booking :coordinate-maintenance-schedule-proposal
                :coordinate-supply-request :coordinate-guest-services-logistics]]
      (is (true? (phase/auto-commits-at-phase? 2 op))))
    (is (false? (phase/auto-commits-at-phase? 2 :flag-safety-concern)))))

(deftest phase-3-full-auto-commit-except-safety
  (testing "Phase 3: all non-safety ops auto-commit; safety never does"
    (doseq [op [:schedule-facility-booking :coordinate-maintenance-schedule-proposal
                :coordinate-supply-request :coordinate-guest-services-logistics]]
      (is (true? (phase/auto-commits-at-phase? 3 op))))
    (is (false? (phase/auto-commits-at-phase? 3 :flag-safety-concern)))))

(deftest always-escalates-only-at-phase-3-for-safety
  (testing "always-escalates? is the phase-3-specific escalation table --
            :flag-safety-concern is the only member"
    (is (true? (phase/always-escalates? 3 :flag-safety-concern)))
    (is (false? (phase/always-escalates? 3 :schedule-facility-booking)))
    (is (false? (phase/always-escalates? 0 :flag-safety-concern))
        "phase 0 has no :always-escalate set at all")))

(deftest safety-concern-never-auto-commits-at-any-phase
  (doseq [ph [0 1 2 3]]
    (is (false? (phase/auto-commits-at-phase? ph :flag-safety-concern)))))
