(ns amusementfacilityops.advisor
  "Proposal generation and rationale for amusement/recreation facility coordination.

  The advisor is the LLM seam: given a facility context and a request type,
  it generates a proposal with a confidence score and reasoning. In production,
  this would be an LLM call; in demo/tests, it's deterministic.

  `Advisor` is the injection-point protocol `amusementfacilityops.operation`
  drives from its `:advise` StateGraph node -- one `advise` call per op,
  dispatching to the SAME five per-op functions below (unchanged) that used
  to be called directly, inline, from the old hand-rolled `operation/advise`
  step function. This is a structural seam, not new domain logic: every op
  it recognizes and every proposal shape it returns is identical to before."
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

;; ---------------------- Advisor protocol (StateGraph injection seam) ----------------------

(defprotocol Advisor
  (advise [a request store]
    "Given a `request` map (must include :operation, plus that op's
    fields -- :facility-id always, others op-specific, see the five
    advise-* fns above) and a Store, return a proposal map, or
    {:status :error :reason ...} if the op is unknown or the facility
    doesn't exist. `amusementfacilityops.operation`'s `:advise` node
    calls this exactly once per graph run."))

(defrecord DefaultAdvisor []
  Advisor
  (advise [_ request store]
    (let [op-type (:operation request)]
      (case op-type
        :schedule-facility-booking
        (advise-booking-proposal
         store (:facility-id request) (:event-name request)
         (:event-date request) (:party-size request))

        :coordinate-maintenance-schedule-proposal
        (advise-maintenance-proposal
         store (:facility-id request) (:attraction-id request)
         (:maintenance-type request) (:scheduled-date request))

        :coordinate-supply-request
        (advise-supply-request
         store (:facility-id request) (:supply-type request)
         (:quantity request) (:requested-delivery-date request))

        :coordinate-guest-services-logistics
        (advise-guest-services
         store (:facility-id request) (:service-type request)
         (:details request))

        :flag-safety-concern
        (advise-safety-concern
         store (:facility-id request) (:concern-type request)
         (:description request) (:severity request))

        {:status :error :reason "Unknown operation type" :operation op-type}))))

(defn default-advisor
  "The default `Advisor` -- deterministic, dispatches to the five
  advise-* functions above by `:operation`. A real LLM advisor is a
  swap of this record for another `Advisor` implementation, not a
  rewrite of `amusementfacilityops.operation`."
  []
  (->DefaultAdvisor))
