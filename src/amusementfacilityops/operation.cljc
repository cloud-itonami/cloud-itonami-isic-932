(ns amusementfacilityops.operation
  "langgraph-clj StateGraph for the amusement/recreation facility coordination actor.

  The state machine flow is:
  intake → advise → govern → decide → {commit | hold | request-approval} | audit

  This is the operational orchestration layer that drives proposals through
  the advisor and governor, and decides on commitment based on governance rules."
  (:require [amusementfacilityops.store :as store]
            [amusementfacilityops.advisor :as advisor]
            [amusementfacilityops.governor :as governor]
            [amusementfacilityops.phase :as phase]))

;; ---------------------- state schema ----------------------

(defn init-state
  "Create initial state for a proposal intake."
  [store request]
  {:store store
   :request request
   :proposal nil
   :governance {:violations [] :decision nil}
   :action nil
   :ledger-entry nil})

;; ---------------------- step functions ----------------------

(defn intake
  "Step 1: Intake the request, identify operation type."
  [state]
  (let [request (:request state)
        op-type (:operation request)]
    (assoc state :intake-op op-type)))

(defn advise
  "Step 2: Advisor generates proposal with confidence and reasoning."
  [state]
  (let [request (:request state)
        store (:store state)
        op-type (:operation request)]
    (case op-type
      :schedule-facility-booking
      (assoc state :proposal
             (advisor/advise-booking-proposal
              store (:facility-id request) (:event-name request)
              (:event-date request) (:party-size request)))

      :coordinate-maintenance-schedule-proposal
      (assoc state :proposal
             (advisor/advise-maintenance-proposal
              store (:facility-id request) (:attraction-id request)
              (:maintenance-type request) (:scheduled-date request)))

      :coordinate-supply-request
      (assoc state :proposal
             (advisor/advise-supply-request
              store (:facility-id request) (:supply-type request)
              (:quantity request) (:requested-delivery-date request)))

      :coordinate-guest-services-logistics
      (assoc state :proposal
             (advisor/advise-guest-services
              store (:facility-id request) (:service-type request)
              (:details request)))

      :flag-safety-concern
      (assoc state :proposal
             (advisor/advise-safety-concern
              store (:facility-id request) (:concern-type request)
              (:description request) (:severity request)))

      (assoc state :proposal {:status :error :reason "Unknown operation type"}))))

(defn govern
  "Step 3: Governor evaluates the proposal against HARD checks."
  [state]
  (let [proposal (:proposal state)
        store (:store state)
        gov-result (governor/govern store proposal)]
    (assoc state :governance gov-result)))

(defn decide
  "Step 4: Decide on next action based on governance decision.
  - If all checks pass: commit (for auto-commit ops) or request-approval (others)
  - If any check fails: hold with violations
  - If escalation-flagged: always escalate"
  [state]
  (let [gov-result (:governance state)
        proposal (:proposal state)
        op-id (:operation proposal)]
    (if (:passes? gov-result)
      (if (or (:escalate? proposal) (= op-id :flag-safety-concern))
        (assoc state :action :escalate)
        (assoc state :action :request-approval))
      (assoc state :action :hold))))

(defn commit
  "Step 5a: Commit proposal to coordination log."
  [state]
  (let [store (:store state)
        proposal (:proposal state)
        ledger-fact {:timestamp (java.util.Date.)
                     :operation (:operation proposal)
                     :facility-id (:facility-id proposal)
                     :status :committed}]
    (store/commit-record! store proposal)
    (store/append-ledger! store ledger-fact)
    (assoc state :action :committed :ledger-entry ledger-fact)))

(defn hold
  "Step 5b: Hold proposal (violations found)."
  [state]
  (let [store (:store state)
        gov-result (:governance state)
        ledger-fact {:timestamp (java.util.Date.)
                     :operation (:operation (:proposal state))
                     :facility-id (:facility-id (:proposal state))
                     :status :held
                     :violations (:violations gov-result)}]
    (store/append-ledger! store ledger-fact)
    (assoc state :action :held :ledger-entry ledger-fact)))

(defn request-approval
  "Step 5c: Request human approval (governance passed, but op requires it)."
  [state]
  (let [store (:store state)
        proposal (:proposal state)
        ledger-fact {:timestamp (java.util.Date.)
                     :operation (:operation proposal)
                     :facility-id (:facility-id proposal)
                     :status :pending-approval}]
    (store/append-ledger! store ledger-fact)
    (assoc state :action :pending-approval :ledger-entry ledger-fact)))

(defn escalate
  "Step 5d: Escalate safety concern or critical proposal."
  [state]
  (let [store (:store state)
        proposal (:proposal state)
        ledger-fact {:timestamp (java.util.Date.)
                     :operation (:operation proposal)
                     :facility-id (:facility-id proposal)
                     :status :escalated}]
    (store/append-ledger! store ledger-fact)
    (assoc state :action :escalated :ledger-entry ledger-fact)))

;; ---------------------- run proposal end-to-end ----------------------

(defn run-proposal
  "Execute the full state machine: intake → advise → govern → decide → action.
  Returns final state with decision and any ledger updates."
  [store request]
  (let [state (init-state store request)]
    (-> state
        intake
        advise
        govern
        decide
        (as-> s (case (:action s)
                  :commit (commit s)
                  :hold (hold s)
                  :request-approval (request-approval s)
                  :escalate (escalate s)
                  s)))))
