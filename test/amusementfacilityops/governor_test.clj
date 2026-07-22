(ns amusementfacilityops.governor-test
  "Tests for the three HARD, permanent, un-overridable governor checks."
  (:require [clojure.test :refer [deftest is testing]]
            [amusementfacilityops.store :as store]
            [amusementfacilityops.governor :as governor]))

(deftest hard-check-1-facility-unverified
  (testing "HARD-check 1: facility must be registered AND verified"
    (let [s (store/make-store)]
      ;; facility-1 is registered and verified
      (is (empty? (governor/facility-unverified-violations s "facility-1")))
      ;; facility-3 is registered but NOT verified
      (is (= 1 (count (governor/facility-unverified-violations s "facility-3"))))
      ;; unknown facility is not registered at all
      (is (= 1 (count (governor/facility-unverified-violations s "no-such-facility")))))))

(deftest hard-check-2-effect-not-propose
  (testing "HARD-check 2: :effect must be :propose"
    (is (empty? (governor/effect-not-propose-violations
                 {:operation :schedule-facility-booking :effect :propose})))
    (is (= 1 (count (governor/effect-not-propose-violations
                      {:operation :schedule-facility-booking :effect :commit}))))
    (is (= 1 (count (governor/effect-not-propose-violations
                      {:operation :schedule-facility-booking :effect :execute}))))))

(deftest hard-check-3-scope-exclusion
  (testing "HARD-check 3: ride-safety sign-off / pricing / go-no-go content is blocked"
    (is (= 1 (count (governor/scope-exclusion-violations
                      {:operation :coordinate-maintenance-schedule-proposal
                       :maintenance-type "safety-inspection-sign-off"}))))
    (is (= 1 (count (governor/scope-exclusion-violations
                      {:operation :coordinate-supply-request
                       :description "update pricing policy"}))))
    (is (= 1 (count (governor/scope-exclusion-violations
                      {:operation :coordinate-maintenance-schedule-proposal
                       :note "operational readiness go/no-go call"}))))))

(deftest flag-safety-concern-not-self-blocked
  (testing ":flag-safety-concern legitimately mentions safety and must NOT
            trip the scope-exclusion check on itself"
    (is (empty? (governor/scope-exclusion-violations
                 {:operation :flag-safety-concern
                  :concern-type "safety-issue"
                  :description "guardrail is loose"})))))

(deftest clean-proposal-has-no-violations
  (testing "a routine, in-scope, :propose proposal against a verified
            facility has zero violations from any of the three checks"
    (is (empty? (governor/scope-exclusion-violations
                 {:operation :schedule-facility-booking
                  :event-name "Team building"})))))

(deftest govern-passes-for-clean-verified-proposal
  (let [s (store/make-store)
        proposal {:operation :schedule-facility-booking
                   :facility-id "facility-1" :effect :propose}
        result (governor/govern s proposal)]
    (is (true? (:passes? result)))
    (is (= :APPROVE (:decision result)))
    (is (empty? (:violations result)))))

(deftest govern-rejects-unverified-facility
  (testing "governor rejection: an unverified facility is a hard reject,
            regardless of how clean the rest of the proposal is"
    (let [s (store/make-store)
          proposal {:operation :schedule-facility-booking
                     :facility-id "facility-3" :effect :propose}
          result (governor/govern s proposal)]
      (is (false? (:passes? result)))
      (is (= :REJECT (:decision result)))
      (is (some #{:facility-unverified} (map :check/id (:violations result)))))))

(deftest govern-rejects-non-propose-effect
  (let [s (store/make-store)
        proposal {:operation :schedule-facility-booking
                   :facility-id "facility-1" :effect :commit}
        result (governor/govern s proposal)]
    (is (false? (:passes? result)))
    (is (= :REJECT (:decision result)))))

(deftest govern-accumulates-multiple-violations
  (testing "multiple independent violations on the SAME proposal all
            surface -- the governor doesn't short-circuit on the first"
    (let [s (store/make-store)
          proposal {:operation :coordinate-maintenance-schedule-proposal
                     :facility-id "no-such-facility" :effect :commit
                     :maintenance-type "safety-inspection-sign-off"}
          result (governor/govern s proposal)]
      (is (= 3 (count (:violations result)))
          "facility-unverified + effect-not-propose + scope-exclusion, all three"))))
