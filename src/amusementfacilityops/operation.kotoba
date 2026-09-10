(ns amusementfacilityops.operation
  "OperationActor -- one amusement/recreation facility coordination request
  = one supervised actor run, expressed as a REAL compiled `langgraph-clj`
  `StateGraph` (`langgraph.graph/state-graph` + `compile-graph`). The
  advisor (`amusementfacilityops.advisor/Advisor`) is sealed into a single
  node (`:advise`); its proposal is ALWAYS routed through the independent
  `amusementfacilityops.governor` (`:govern`) and the rollout-phase gate
  (`:decide`) before anything commits to the SSoT.

  This replaces the previous `run-proposal`, which was a plain
  `(-> state intake advise govern decide (case action ...))` threading
  pipeline that never required `langgraph.graph` and never touched
  `state-graph`/`add-node`/`compile-graph` at all -- despite this
  namespace's own former docstring calling it \"the langgraph-clj
  StateGraph\". That claim was false; this is the real thing. Two
  concrete structural bugs the old pipeline had, now fixed:

    1. `decide` never emitted `:action :commit` -- the `commit` step
       function existed but nothing in the old `decide` logic could ever
       route to it. Every clean, non-safety proposal fell through to
       `:request-approval` regardless of how low-risk the op was.
    2. `amusementfacilityops.phase`'s auto-commit table (`phases`,
       `auto-commits-at-phase?`) was fully defined and unit-tested in
       isolation, but the old `decide` never called it -- phase rollout
       was inert decoration, not a real gate.

  Both are fixed here by genuinely wiring `phase/auto-commits-at-phase?`
  into `:decide`: a clean, non-escalating proposal now actually reaches
  `:commit` when its op is in the current phase's auto-commit set, and
  is held (distinguished by `:reason :not-in-phase-auto-set`) otherwise.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`amusementfacilityops.store/MemStore`, or any `Store` impl)
    - the Advisor  (`amusementfacilityops.advisor/default-advisor` by
                     default -- a thin protocol seam over the SAME five
                     per-op advisor functions the old pipeline called
                     inline, unchanged; a real LLM advisor is a swap)
    - the Phase    (0->3 rollout; passed per-request via `:phase-num`,
                     not frozen at `build` time)

  One graph run = one facility-coordination request. No unbounded inner
  loop -- each run is auditable and checkpointed. Every commit/hold
  decision fact lands in `amusementfacilityops.store`'s append-only
  ledger (`store/append-ledger!`) -- that call was already genuinely
  wired (not dead code) in the pre-graph pipeline, and that wiring is
  preserved here, now reachable from both the `:commit` and `:hold`
  terminal nodes with the SAME ledger-fact shape (`:timestamp
  :operation :facility-id :status ...`) the old pipeline used.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human operator resumes it with a
  decision. `:flag-safety-concern` ALWAYS reaches this node -- see
  `escalate?` below, which agrees with `amusementfacilityops.governor`'s
  scope-exclusion allowance for that op and with `phase/always-escalates?`
  at phase 3."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [amusementfacilityops.advisor :as advisor]
            [amusementfacilityops.governor :as governor]
            [amusementfacilityops.phase :as phase]
            [amusementfacilityops.store :as store]))

;; ---------------------- portable timestamp ----------------------

(defn- now []
  #?(:clj (java.util.Date.)
     :cljs (js/Date.)))

;; ---------------------- audit-fact builders ----------------------
;; Same ledger-fact shape the pre-graph pipeline used:
;; {:timestamp .. :operation .. :facility-id .. :status .. [:violations ..]}

(defn- hold-fact
  [request proposal violations reason]
  {:timestamp (now)
   :operation (:operation request)
   :facility-id (:facility-id proposal)
   :status :held
   :reason reason
   :violations violations})

(defn- commit-fact
  [request proposal approval]
  (cond-> {:timestamp (now)
           :operation (:operation request)
           :facility-id (:facility-id proposal)
           :status :committed
           :proposal proposal}
    approval (assoc :approved-by (:by approval))))

(defn- approval-requested-fact
  [request proposal phase-num reason]
  {:timestamp (now)
   :operation (:operation request)
   :facility-id (:facility-id proposal)
   :status :pending-approval
   :reason reason
   :phase phase-num
   :confidence (:confidence proposal)})

;; ---------------------- escalation predicate ----------------------

(defn escalate?
  "A clean proposal escalates to human approval (`:request-approval`,
  a real `interrupt-before` pause) when: the advisor itself flagged it
  (`:escalate?`, currently only ever set by `advisor/advise-safety-concern`),
  OR the op is `:flag-safety-concern` (belt-and-suspenders -- agrees with
  `amusementfacilityops.phase/always-escalates?` at phase 3, and with
  `amusementfacilityops.governor`'s allowance for that op alone in its
  scope-exclusion scan)."
  [proposal phase-num]
  (boolean (or (:escalate? proposal)
               (= :flag-safety-concern (:operation proposal))
               (phase/always-escalates? phase-num (:operation proposal)))))

;; ---------------------- compiled StateGraph ----------------------

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- an `amusementfacilityops.advisor/Advisor`
                      (default: `advisor/default-advisor`)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :phase-num ..}` (phase is
  per-request, not frozen at `build` time)."
  [store & [{:keys [advisor checkpointer]
             :or {advisor (advisor/default-advisor)
                  checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request {:default nil}
         :phase-num {:default 0}
         :proposal {:default nil}
         :violations {:default nil}
         :decision {:default nil}
         :approval {:default nil}
         :audit {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          {:proposal (advisor/advise advisor request store)}))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:violations (:violations (governor/govern store proposal))}))

      (g/add-node :decide
        (fn [{:keys [request proposal violations phase-num]}]
          (let [clean? (empty? violations)
                escalating? (and clean? (escalate? proposal phase-num))
                auto-commit? (and clean? (not escalating?)
                                   (phase/auto-commits-at-phase?
                                    phase-num (:operation proposal)))]
            (cond
              ;; HARD governor violations are a permanent block -- NEVER
              ;; routed through human approval, straight to :hold.
              (not clean?)
              {:decision :hold
               :audit [(hold-fact request proposal violations :governor-violation)]}

              escalating?
              {:decision :escalate
               :audit [(approval-requested-fact
                        request proposal phase-num
                        (if (= :flag-safety-concern (:operation proposal))
                          :always-escalate
                          :advisor-escalation))]}

              auto-commit?
              {:decision :commit}

              :else
              {:decision :hold
               :audit [(hold-fact request proposal violations :not-in-phase-auto-set)]}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval violations]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:timestamp (now) :operation (:operation request)
                      :facility-id (:facility-id proposal)
                      :status :approval-granted :by (:by approval)}]}
            {:decision :hold
             :audit [(assoc (hold-fact request proposal violations :approver-rejected)
                            :status :approval-rejected)]})))

      (g/add-node :commit
        (fn [{:keys [request proposal approval]}]
          (store/commit-record! store proposal)
          (let [f (commit-fact request proposal approval)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:held :approval-rejected} (:status %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer checkpointer
        :interrupt-before #{:request-approval}})))
