(ns amusementfacilityops.store-test
  "Tests for the ISIC-932 MemStore SSoT: facility/booking directories,
  the append-only ledger, and the coordination-log."
  (:require [clojure.test :refer [deftest is testing]]
            [amusementfacilityops.store :as store]))

(deftest facility-lookup
  (testing "a seeded, verified facility is found by :facility-id"
    (let [s (store/make-store)]
      (is (= "Central Amusement Park" (:name (store/facility s "facility-1"))))
      (is (true? (:registered? (store/facility s "facility-1"))))
      (is (true? (:verified? (store/facility s "facility-1")))))))

(deftest facility-lookup-miss
  (testing "an unknown facility-id returns nil, not an exception"
    (let [s (store/make-store)]
      (is (nil? (store/facility s "does-not-exist"))))))

(deftest all-facilities-count
  (testing "demo-data seeds exactly 3 facilities"
    (let [s (store/make-store)]
      (is (= 3 (count (store/all-facilities s)))))))

(deftest booking-lookup
  (testing "a seeded booking is found by :booking-id"
    (let [s (store/make-store)]
      (is (= "Private Group Event" (:event-name (store/booking s "booking-1")))))))

(deftest booking-lookup-miss
  (testing "an unknown booking-id returns nil"
    (let [s (store/make-store)]
      (is (nil? (store/booking s "does-not-exist"))))))

(deftest ledger-starts-empty-and-is-append-only
  (testing "a fresh store's ledger is empty; append-ledger! grows it by
            exactly one entry per call, never mutating prior entries"
    (let [s (store/make-store)]
      (is (empty? (store/ledger s)))
      (store/append-ledger! s {:event "fact-1"})
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s {:event "fact-2"})
      (is (= 2 (count (store/ledger s))))
      (is (= [{:event "fact-1"} {:event "fact-2"}] (store/ledger s))
          "insertion order is preserved"))))

(deftest coordination-log-starts-empty-and-records-commits
  (testing "a fresh store's coordination-log is empty; commit-record!
            grows it by exactly one entry per call"
    (let [s (store/make-store)]
      (is (empty? (store/coordination-log s)))
      (store/commit-record! s {:operation :schedule-facility-booking})
      (is (= 1 (count (store/coordination-log s)))))))

(deftest with-facilities-replaces-directory
  (testing "with-facilities replaces the seeded facility directory wholesale"
    (let [s (store/make-store)]
      (store/with-facilities s {"only-one" {:facility-id "only-one"
                                             :registered? true :verified? true}})
      (is (= 1 (count (store/all-facilities s))))
      (is (nil? (store/facility s "facility-1")) "the old seed data is gone"))))
