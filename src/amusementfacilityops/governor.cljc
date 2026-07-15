(ns amusementfacilityops.governor
  "Governor with three HARD, permanent, un-overridable checks for the
  amusement/recreation facility coordination actor.

  1. Facility/booking-record unverified — target must exist in store AND be
     independently :registered?/:verified?, re-derived every time.
  2. Effect not :propose — rejected outright.
  3. Scope exclusion — any proposal touching ride-safety-inspection sign-offs,
     pricing/programming policy, operational-readiness decisions, or safety-
     authority overrides is permanently blocked."
  (:require [amusementfacilityops.store :as store]
            [clojure.string :as str]))

;; ---------------------- hard checks ----------------------

(defn facility-unverified-violations
  "Check 1: Facility must be registered AND verified.
  This is re-derived from the facility's own :registered?/:verified? fields,
  never from proposal self-report."
  [store facility-id]
  (let [facility (store/facility store facility-id)]
    (cond
      (nil? facility)
      [{:check/id :facility-unverified
        :violation "Facility not found in store"}]

      (not (:registered? facility))
      [{:check/id :facility-unverified
        :violation "Facility is not registered"}]

      (not (:verified? facility))
      [{:check/id :facility-unverified
        :violation "Facility is not verified"}]

      :else
      [])))

(defn effect-not-propose-violations
  "Check 2: Effect must be :propose. Any other effect is rejected outright."
  [proposal]
  (if (not= (:effect proposal) :propose)
    [{:check/id :effect-not-propose
      :violation (str "Effect is " (:effect proposal) ", not :propose")}]
    []))

(defn scope-exclusion-violations
  "Check 3: Block proposals touching excluded territory.
  Excluded: ride-safety-inspection sign-offs, pricing/programming policy,
  operational-readiness/go-no-go decisions, safety-authority overrides.

  Uses qualified substring scan (EN+JA) so legitimate :flag-safety-concern
  ops that mention 'safety' aren't self-blocked."
  [proposal]
  (let [forbidden-patterns
        [;; EN patterns
         #"(?i)ride.*safety.*inspection"
         #"(?i)safety.*sign.?off"
         #"(?i)safety.*certification"
         #"(?i)go.?no.?go"
         #"(?i)operational.?readiness"
         #"(?i)pricing.*policy"
         #"(?i)programming.*policy"
         #"(?i)safety.?authority"
         ;; JA patterns (common amusement facility terminology)
         #"安全.?検査.?認可"
         #"営業.?判定"
         #"運行.?可否"
         #"料金.?ポリシー"
         #"プログラミング.?方針"]

        ;; Allowed operations that legitimately mention safety
        allowed-ops #{:flag-safety-concern}

        op-id (:operation proposal)
        proposal-str (str proposal)]

    (if (allowed-ops op-id)
      ;; :flag-safety-concern is allowed (it always escalates)
      []
      ;; For all other ops, scan for forbidden patterns
      (if (some #(re-find % proposal-str) forbidden-patterns)
        [{:check/id :scope-exclusion
          :violation "Proposal touches ride-safety-inspection, pricing/programming policy, or safety-authority overrides"}]
        []))))

;; ---------------------- decision logic ----------------------

(defn govern
  "Apply all three HARD checks. Any violation is a permanent rejection
  with no override path."
  [store proposal]
  (let [facility-violations (facility-unverified-violations store (:facility-id proposal))
        effect-violations (effect-not-propose-violations proposal)
        scope-violations (scope-exclusion-violations proposal)
        all-violations (concat facility-violations effect-violations scope-violations)]

    {:proposal proposal
     :violations all-violations
     :passes? (empty? all-violations)
     :decision (if (empty? all-violations)
                 :APPROVE
                 :REJECT)}))
