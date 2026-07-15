(ns amusementfacilityops.advisor
  "Proposal generation and rationale for amusement/recreation facility coordination.

  The advisor is the LLM seam: given a facility context and a request type,
  it generates a proposal with a confidence score and reasoning. In production,
  this would be an LLM call; in demo/tests, it's deterministic."
  (:require [amusementfacilityops.store :as store]))

;; ---------------------- deterministic demo advisor ----------------------

(defn advise-booking-proposal
  "Generate a booking-scheduling proposal for a facility."
  [store facility-id event-name event-date party-size]
  (let [facility (store/facility store facility-id)]
    (if-not facility
      {:status :error :reason "Facility not found"}
      {:operation :schedule-facility-booking
       :facility-id facility-id
       :event-name event-name
       :event-date event-date
       :party-size party-size
       :effect :propose
       :confidence 0.85
       :reasoning "Booking request within facility capacity and standard hours"})))

(defn advise-maintenance-proposal
  "Generate an administrative maintenance-scheduling proposal."
  [store facility-id attraction-id maintenance-type scheduled-date]
  (let [facility (store/facility store facility-id)]
    (if-not facility
      {:status :error :reason "Facility not found"}
      {:operation :coordinate-maintenance-schedule-proposal
       :facility-id facility-id
       :attraction-id attraction-id
       :maintenance-type maintenance-type
       :scheduled-date scheduled-date
       :effect :propose
       :confidence 0.78
       :reasoning "Maintenance scheduling proposal for administrative coordination (not safety sign-off)"})))

(defn advise-supply-request
  "Generate a non-safety-critical supply coordination proposal."
  [store facility-id supply-type quantity requested-delivery-date]
  (let [facility (store/facility store facility-id)]
    (if-not facility
      {:status :error :reason "Facility not found"}
      {:operation :coordinate-supply-request
       :facility-id facility-id
       :supply-type supply-type
       :quantity quantity
       :requested-delivery-date requested-delivery-date
       :effect :propose
       :confidence 0.82
       :reasoning "Non-safety-critical consumables request for guest services or operations"})))

(defn advise-guest-services
  "Generate a guest-services logistics coordination proposal."
  [store facility-id service-type details]
  (let [facility (store/facility store facility-id)]
    (if-not facility
      {:status :error :reason "Facility not found"}
      {:operation :coordinate-guest-services-logistics
       :facility-id facility-id
       :service-type service-type
       :details details
       :effect :propose
       :confidence 0.80
       :reasoning "Guest services logistics coordination for check-in/ticketing/wayfinding"})))

(defn advise-safety-concern
  "Generate a safety-concern escalation (always auto-escalates)."
  [store facility-id concern-type description severity]
  (let [facility (store/facility store facility-id)]
    (if-not facility
      {:status :error :reason "Facility not found"}
      {:operation :flag-safety-concern
       :facility-id facility-id
       :concern-type concern-type
       :description description
       :severity severity
       :effect :propose
       :confidence 0.95
       :reasoning "Safety concern flagged for immediate escalation"
       :escalate? true})))
