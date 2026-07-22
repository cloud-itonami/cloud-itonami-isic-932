(ns amusementfacilityops.advisor-test
  "Tests for the five deterministic per-op advisor functions and the
  `Advisor` protocol seam (`default-advisor`) that
  `amusementfacilityops.operation`'s `:advise` StateGraph node drives."
  (:require [clojure.test :refer [deftest is testing]]
            [amusementfacilityops.store :as store]
            [amusementfacilityops.advisor :as advisor]))

(deftest advise-booking-proposal-happy-path
  (testing "a booking proposal for a known facility always proposes (never actuates)"
    (let [s (store/make-store)
          p (advisor/advise-booking-proposal s "facility-1" "Team Event" "2026-08-01" 50)]
      (is (= :schedule-facility-booking (:operation p)))
      (is (= :propose (:effect p)))
      (is (= "facility-1" (:facility-id p))))))

(deftest advise-booking-proposal-unknown-facility
  (testing "a booking proposal for an unknown facility errors instead of guessing"
    (let [s (store/make-store)
          p (advisor/advise-booking-proposal s "no-such-facility" "Team Event" "2026-08-01" 50)]
      (is (= :error (:status p))))))

(deftest advise-maintenance-proposal-is-administrative-only
  (testing "maintenance proposals are :propose only, never a sign-off"
    (let [s (store/make-store)
          p (advisor/advise-maintenance-proposal s "facility-1" "attr-1" "quarterly" "2026-08-01")]
      (is (= :coordinate-maintenance-schedule-proposal (:operation p)))
      (is (= :propose (:effect p))))))

(deftest advise-supply-request-happy-path
  (let [s (store/make-store)
        p (advisor/advise-supply-request s "facility-2" "beverages" 100 "2026-08-01")]
    (is (= :coordinate-supply-request (:operation p)))
    (is (= :propose (:effect p)))))

(deftest advise-guest-services-happy-path
  (let [s (store/make-store)
        p (advisor/advise-guest-services s "facility-1" "wayfinding" "entrance kiosk")]
    (is (= :coordinate-guest-services-logistics (:operation p)))
    (is (= :propose (:effect p)))))

(deftest advise-safety-concern-always-flags-escalate
  (testing "a safety concern proposal ALWAYS carries :escalate? true --
            this is the only advisor fn that sets it"
    (let [s (store/make-store)
          p (advisor/advise-safety-concern s "facility-1" "equipment-malfunction"
                                            "Motor vibration" :high)]
      (is (= :flag-safety-concern (:operation p)))
      (is (= :propose (:effect p)))
      (is (true? (:escalate? p))))))

;; ---------------------- Advisor protocol seam ----------------------

(deftest default-advisor-dispatches-by-operation
  (testing "default-advisor routes each op to the SAME per-op fn tested
            above -- the protocol is a dispatch seam, not new logic"
    (let [s (store/make-store)
          a (advisor/default-advisor)]
      (is (= :schedule-facility-booking
             (:operation (advisor/advise a {:operation :schedule-facility-booking
                                             :facility-id "facility-1"
                                             :event-name "E" :event-date "2026-08-01"
                                             :party-size 10}
                                          s))))
      (is (= :flag-safety-concern
             (:operation (advisor/advise a {:operation :flag-safety-concern
                                             :facility-id "facility-1"
                                             :concern-type "x" :description "y" :severity :low}
                                          s)))))))

(deftest default-advisor-unknown-op-errors
  (testing "an unrecognized :operation is an error, not a silent fallback proposal"
    (let [s (store/make-store)
          a (advisor/default-advisor)
          p (advisor/advise a {:operation :not-a-real-op :facility-id "facility-1"} s)]
      (is (= :error (:status p))))))
