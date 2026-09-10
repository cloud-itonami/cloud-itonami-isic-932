(ns amusementfacilityops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  demo page and no generator at all. This namespace drives the REAL
  actor stack -- `amusementfacilityops.operation` (a genuinely compiled
  `langgraph.graph` StateGraph) -> `amusementfacilityops.governor` ->
  `amusementfacilityops.store` -- through a scenario built on this
  repo's own seed directory (`store/demo-data`: `facility-1`..
  `facility-3`, `booking-1`..`booking-2`) and renders the resulting
  store deterministically.

  Every entity, identifier, rule name, phase and violation string on the
  generated page is read back out of the store or out of
  `amusementfacilityops.phase` after the run. Nothing on the page is
  hand-typed domain data; there is no mock HTML. The one deliberately
  static element is the prose in section headers.

  DETERMINISM: ledger facts carry a `:timestamp` (`java.util.Date`).
  None of it is rendered -- `render` selects columns explicitly and
  never dumps a raw fact -- so two runs against the same seed are
  byte-identical. `-main` asserts this by scanning its own output for a
  leaked `Date` rendering before writing.

  BUILD-TIME INVARIANT: `-main` throws unless the run produced at least
  one HARD governor hold. A console that cannot show the governor
  refusing something is not evidence that the governor works, so the
  requirement is enforced by the build rather than left to convention.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [amusementfacilityops.store :as store]
            [amusementfacilityops.operation :as op]
            [amusementfacilityops.phase :as phase]
            [langgraph.graph :as g]))

;; ----------------------------- the run -----------------------------

(def ^:private approver "ops-manager-01")

(defn- exec!
  "One graph run = one coordination request. Returns the run result."
  [actor tid request phase-num]
  (g/run* actor {:request request :phase-num phase-num} {:thread-id tid}))

(defn- resume!
  "Resumes a run paused at the `interrupt-before #{:request-approval}`
  node with a human operator's decision."
  [actor tid status]
  (g/run* actor {:approval {:status status :by approver}}
          {:thread-id tid :resume? true}))

(def ^:private scenario
  "Nine coordination requests over this repo's own seed directory,
  chosen so that between them they reach every terminal the actor has
  and fire all three of the governor's HARD checks.

  `:approve` -- resume the interrupt with that approval status.
  Ops and facility ids are exactly those in `store/demo-data` and
  `advisor/DefaultAdvisor`'s dispatch table; `facility-99` is
  deliberately absent from the directory."
  [{:tid "s1-booking"
    :label "Group-event booking at a verified park"
    :phase 3
    :request {:operation :schedule-facility-booking
              :facility-id "facility-1"
              :event-name "Corporate Team Building"
              :event-date "2026-07-25"
              :party-size 120}}

   {:tid "s2-guest-services"
    :label "Guest check-in / wayfinding logistics"
    :phase 3
    :request {:operation :coordinate-guest-services-logistics
              :facility-id "facility-2"
              :service-type "wayfinding"
              :details "Add seasonal signage for the east entrance"}}

   {:tid "s3-supply-phase0"
    :label "Consumables supply, actor still at phase 0"
    :phase 0
    :request {:operation :coordinate-supply-request
              :facility-id "facility-2"
              :supply-type "guest-beverages"
              :quantity 500
              :requested-delivery-date "2026-07-20"}}

   {:tid "s4-safety-approved"
    :label "Safety concern -> escalates -> approved"
    :phase 3
    :approve :approved
    :request {:operation :flag-safety-concern
              :facility-id "facility-1"
              :concern-type "equipment-malfunction"
              :description "Carousel motor showing unusual vibration"
              :severity :high}}

   {:tid "s5-safety-rejected"
    :label "Safety concern -> escalates -> rejected by approver"
    :phase 3
    :approve :rejected
    :request {:operation :flag-safety-concern
              :facility-id "facility-2"
              :concern-type "crowd-density"
              :description "Queue overflow past the marked holding area"
              :severity :medium}}

   {:tid "s6-unverified"
    :label "Guest-services request against an UNVERIFIED facility"
    :phase 3
    :request {:operation :coordinate-guest-services-logistics
              :facility-id "facility-3"
              :service-type "ticketing"
              :details "Open a second ticket window"}}

   {:tid "s7-maintenance"
    :label "Routine, entirely benign maintenance scheduling"
    :phase 3
    :request {:operation :coordinate-maintenance-schedule-proposal
              :facility-id "facility-1"
              :attraction-id "carousel-1"
              :maintenance-type "lubrication"
              :scheduled-date "2026-08-01"}}

   {:tid "s8-excluded-scope"
    :label "Operator tries to route a ride-safety sign-off through a permitted op"
    :phase 3
    :request {:operation :coordinate-guest-services-logistics
              :facility-id "facility-1"
              :service-type "ride-safety-inspection-signoff"
              :details "Sign off the coaster inspection so we can open"}}

   {:tid "s9-unknown-facility"
    :label "Booking for a facility that is not in the directory"
    :phase 3
    :request {:operation :schedule-facility-booking
              :facility-id "facility-99"
              :event-name "Offsite"
              :event-date "2026-08-02"
              :party-size 20}}])

(defn run-demo!
  "Drives `scenario` through a fresh seeded store and one compiled
  OperationActor. Returns `{:db .. :runs [..]}` where each run carries
  the terminal state the graph actually reached -- no post-hoc
  narration."
  []
  (let [db (store/make-store)
        actor (op/build db)
        runs (mapv (fn [{:keys [tid request phase approve] :as step}]
                     (let [r (exec! actor tid request phase)
                           r (if approve (resume! actor tid approve) r)]
                       (assoc step
                              :status (:status r)
                              :decision (get-in r [:state :decision])
                              :violations (get-in r [:state :violations]))))
                   scenario)]
    {:db db :runs runs}))

;; --------------------------- derived reads ---------------------------

(defn governor-holds
  "The HARD governor holds in a ledger.

  This repo spells a governor hold `{:status :held :reason
  :governor-violation}`; a `:not-in-phase-auto-set` hold is a rollout
  decision, not a governor refusal, and an `:approval-rejected` fact is
  a human's decision. Only the first kind counts."
  [ledger]
  (filter #(and (= :held (:status %))
                (= :governor-violation (:reason %)))
          ledger))

(defn- rules-fired
  "The distinct `:check/id`s in one fact's violations, in the order the
  governor concatenated them."
  [fact]
  (distinct (keep :check/id (:violations fact))))

(defn- approver-keys-present?
  "Does this committed coordination record itself carry attribution?

  Derived, never asserted: the page re-checks the record's own keys
  every build, so if the commit path is later changed to retain the
  approver, the disclosure below corrects itself with no edit here."
  [record]
  (boolean (some #(contains? record %)
                 [:approved-by :approver :approval :by])))

(defn- commit-fact-for
  "Joins a committed coordination record back to the ledger commit fact
  it came from. `operation`'s `:commit` node stores the same proposal
  map in both places, so equality on `:proposal` is a real join, not a
  positional guess."
  [ledger record]
  (first (filter #(and (= :committed (:status %))
                       (= record (:proposal %)))
                 ledger)))

(defn- unreachable-auto-commit-ops
  "Ops that a phase's `:auto-commit` set promises will auto-commit, but
  whose promised path this run found to be unreachable.

  The discriminator is deliberately narrow: an op qualifies only if it
  was HARD-blocked on `:scope-exclusion` AND never reached a commit
  anywhere in the same run. An op that blocked once and committed
  elsewhere is not unreachable -- that is the governor correctly
  refusing one particular request, which is the behaviour we want, not
  a contradiction. (Concretely: `:coordinate-guest-services-logistics`
  blocks when an operator smuggles ride-safety sign-off text into it,
  yet commits fine for a benign request, so it must NOT be reported
  here; `:coordinate-maintenance-schedule-proposal` blocks even when
  entirely benign, so it must.)

  Derived from `phase/phases` and the run's own ledger and coordination
  log, so this reports nothing once the contradiction is resolved."
  [ledger records]
  (let [promised (into #{} (mapcat (comp :auto-commit val)) phase/phases)
        blocked (into #{} (comp (filter #(some #{:scope-exclusion} (rules-fired %)))
                                (map :operation))
                      (governor-holds ledger))
        committed (into #{} (map :operation) records)]
    (sort (map name (filter #(and (blocked %) (not (committed %))) promised)))))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if v (esc (name v)) ""))

(defn- yes-no [b]
  (if b "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>"))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [f coll] (str/join "\n" (map f coll)))

;; --- facilities -------------------------------------------------------

(defn- facility-gate
  "What the governor's check 1 will do with this facility, re-derived
  from the facility record's own fields exactly as
  `governor/facility-unverified-violations` does."
  [{:keys [registered? verified?]}]
  (cond
    (not registered?) "<span class=\"critical\">HARD block &middot; not registered</span>"
    (not verified?) "<span class=\"critical\">HARD block &middot; not verified</span>"
    :else "<span class=\"ok\">may propose</span>"))

(defn- facility-row [{:keys [facility-id name address attractions-count] :as f}]
  (row (str "<code>" (esc facility-id) "</code>")
       (esc name)
       (esc address)
       (str "<span class=\"num\">" (esc attractions-count) "</span>")
       (yes-no (:registered? f))
       (yes-no (:verified? f))
       (facility-gate f)))

;; --- bookings ---------------------------------------------------------

(defn- booking-row [{:keys [booking-id facility-id event-name event-date party-size status]}]
  (row (str "<code>" (esc booking-id) "</code>")
       (str "<code>" (esc facility-id) "</code>")
       (esc event-name)
       (esc event-date)
       (str "<span class=\"num\">" (esc party-size) "</span>")
       (kw status)))

;; --- phases -----------------------------------------------------------

(defn- phase-row [[id {:keys [name auto-commit always-escalate description]}]]
  (row (str "<span class=\"num\">" (esc id) "</span>")
       (esc name)
       (if (seq auto-commit)
         (str/join ", " (map #(str "<code>" (kw %) "</code>") (sort (map clojure.core/name auto-commit))))
         "<span class=\"muted\">none</span>")
       (if (seq always-escalate)
         (str/join ", " (map #(str "<code>" (kw %) "</code>") (sort (map clojure.core/name always-escalate))))
         "<span class=\"muted\">&mdash;</span>")
       (esc description)))

;; --- governor checks --------------------------------------------------

(def ^:private check-descriptions
  "The three HARD checks, keyed by the `:check/id` the governor itself
  emits. Prose only -- the fire counts beside them are counted from the
  run's own ledger."
  [[:facility-unverified
    "Target facility must exist in the store AND be independently <code>:registered?</code> and <code>:verified?</code>, re-derived from the facility record every time &mdash; never from proposal self-report."]
   [:effect-not-propose
    "The proposal's <code>:effect</code> must be <code>:propose</code>. Anything else is rejected outright, so a malformed or error proposal cannot commit."]
   [:scope-exclusion
    "Any proposal touching ride-safety-inspection sign-offs, pricing/programming policy, operational-readiness go/no-go, or safety-authority overrides is permanently blocked. <code>:flag-safety-concern</code> is the one op exempted from the scan, because flagging a hazard must stay possible."]])

(defn- check-row [ledger [check-id description]]
  (let [n (count (filter #(some #{check-id} (rules-fired %)) (governor-holds ledger)))]
    (row (str "<code>" (kw check-id) "</code>")
         description
         (str "<span class=\"num\">" n "</span>")
         (if (pos? n)
           "<span class=\"critical\">fired this run</span>"
           "<span class=\"muted\">not exercised</span>"))))

;; --- scenario outcomes ------------------------------------------------

(defn- outcome-cell [{:keys [decision violations approve]}]
  (cond
    (and (= :hold decision) (seq violations))
    (str "<span class=\"critical\">HARD hold &middot; "
         (str/join ", " (map #(kw %) (rules-fired {:violations violations})))
         "</span>")
    (= :hold decision) (if (= :rejected approve)
                         "<span class=\"warn\">rejected by approver</span>"
                         "<span class=\"warn\">held &middot; not in phase auto-commit set</span>")
    (= :commit decision) (if approve
                           "<span class=\"ok\">approved &amp; committed</span>"
                           "<span class=\"ok\">auto-committed</span>")
    :else "<span class=\"muted\">in progress</span>"))

(defn- scenario-row [{:keys [tid label request phase] :as r}]
  (row (str "<code>" (esc tid) "</code>")
       (esc label)
       (str "<code>" (kw (:operation request)) "</code>")
       (str "<code>" (esc (:facility-id request)) "</code>")
       (str "<span class=\"num\">" (esc phase) "</span>")
       (outcome-cell r)))

;; --- ledger -----------------------------------------------------------

(defn- status-cell [{:keys [status]}]
  (case status
    :committed "<span class=\"ok\">committed</span>"
    :held "<span class=\"critical\">held</span>"
    :approval-rejected "<span class=\"warn\">approval rejected</span>"
    :pending-approval "<span class=\"warn\">pending approval</span>"
    :approval-granted "<span class=\"ok\">approval granted</span>"
    (str "<span class=\"muted\">" (kw status) "</span>")))

(defn- ledger-row [{:keys [operation facility-id reason approved-by] :as f}]
  (row (status-cell f)
       (str "<code>" (kw operation) "</code>")
       (str "<code>" (esc facility-id) "</code>")
       (if reason (str "<code>" (kw reason) "</code>") "<span class=\"muted\">&mdash;</span>")
       (let [rs (rules-fired f)]
         (if (seq rs)
           (str/join ", " (map #(str "<span class=\"critical\">" (kw %) "</span>") rs))
           "<span class=\"muted\">&mdash;</span>"))
       (if approved-by (esc approved-by) "<span class=\"muted\">&mdash;</span>")))

;; --- committed records + approver disclosure --------------------------

(defn- record-row [ledger record]
  (let [fact (commit-fact-for ledger record)
        in-record? (approver-keys-present? record)
        ledger-approver (:approved-by fact)]
    (row (str "<code>" (kw (:operation record)) "</code>")
         (str "<code>" (esc (:facility-id record)) "</code>")
         (str "<span class=\"num\">" (esc (:confidence record)) "</span>")
         (esc (:reasoning record))
         (cond
           in-record?
           (str "<span class=\"ok\">" (esc (or (:approved-by record) (:by record))) "</span>")

           ledger-approver
           (str "<span class=\"warn\">" (esc ledger-approver) "</span> "
                "<span class=\"muted\">(audit only &mdash; not retained in the committed record)</span>")

           :else
           "<span class=\"muted\">auto-committed &mdash; no approver</span>"))))

;; --- document ---------------------------------------------------------

(defn render
  "Renders the console from a store `db` that has already been driven by
  `run-demo!` (or any other real scenario) plus that run's results."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        records (vec (store/coordination-log db))
        facilities (sort-by :facility-id (store/all-facilities db))
        bookings (sort-by :booking-id (store/all-bookings db))
        holds (governor-holds ledger)
        unreachable (unreachable-auto-commit-ops ledger records)
        attribution-gap (seq (filter #(and (not (approver-keys-present? %))
                                           (:approved-by (commit-fact-for ledger %)))
                                     records))]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-932 &middot; amusement &amp; recreation facility operations</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Amusement &amp; recreation facility operations coordination (ISIC 932) &mdash; Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> <span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">safety concerns always escalate</span></p>\n"
     "<p class=\"subtitle\">Build-time output of <code>amusementfacilityops.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>). Every row below was read back out of "
     "<code>amusementfacilityops.store</code> after driving "
     "<code>amusementfacilityops.operation</code>&rsquo;s compiled <code>langgraph</code> StateGraph over this "
     "repo&rsquo;s own seed directory. Timestamps are deliberately omitted so the page is byte-identical "
     "across rebuilds.</p>\n"

     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Facility directory</h2>\n"
     "    <p class=\"muted\">Seeded by <code>store/demo-data</code>. The gate column re-derives check 1 from each "
     "record&rsquo;s own <code>:registered?</code>/<code>:verified?</code> fields, the same way the governor does.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Facility</th><th>Name</th><th>Address</th><th>Attractions</th><th>Registered</th><th>Verified</th><th>Governor gate</th></tr></thead>\n"
     "      <tbody>\n" (rows facility-row facilities) "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Booking directory</h2>\n"
     "    <p class=\"muted\">Seed state. This actor appends committed proposals to the coordination log; it does "
     "not mutate the booking directory, so these rows are unchanged by the run below.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Booking</th><th>Facility</th><th>Event</th><th>Date</th><th>Party size</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n" (rows booking-row bookings) "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Rollout phase gate</h2>\n"
     "    <p class=\"muted\">Read directly from <code>amusementfacilityops.phase/phases</code>. Phase is supplied "
     "per request, not frozen when the actor is built.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Name</th><th>Auto-commits</th><th>Always escalates</th><th>Description</th></tr></thead>\n"
     "      <tbody>\n" (rows phase-row (sort-by key phase/phases)) "\n      </tbody>\n"
     "    </table>\n"
     (when (seq unreachable)
       (str "    <p class=\"note\"><strong class=\"critical\">Observed contradiction.</strong> "
            "The table above promises that "
            (str/join ", " (map #(str "<code>" (esc %) "</code>") unreachable))
            " auto-commits, but this run watched the governor HARD-block it on <code>scope-exclusion</code> "
            "even for an entirely benign request, and never once let it commit. "
            "<code>governor/scope-exclusion-violations</code> scans "
            "<code>(str proposal)</code>, and the advisor&rsquo;s own boilerplate rationale for that op contains the "
            "phrase &ldquo;not safety sign-off&rdquo;; a substring scan cannot tell a denial from an assertion, so the op "
            "blocks itself. The promised auto-commit path is unreachable. This paragraph is derived by "
            "intersecting the phase table with the run&rsquo;s ledger, so it disappears once the contradiction "
            "is fixed.</p>\n"))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor HARD checks</h2>\n"
     "    <p class=\"muted\">All three are permanent and un-overridable. A HARD hold never reaches a human "
     "operator &mdash; <code>operation</code>&rsquo;s <code>:decide</code> node routes it straight to "
     "<code>:hold</code>, bypassing the approval interrupt entirely. Fire counts are counted from this "
     "run&rsquo;s ledger.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Check</th><th>Rule</th><th>Holds</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n" (rows (partial check-row ledger) check-descriptions) "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Scenario runs</h2>\n"
     "    <p class=\"muted\">One row per graph run. Each is a separate checkpointed thread; the two safety "
     "concerns genuinely paused at <code>interrupt-before #{:request-approval}</code> and were resumed with an "
     "operator decision.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Thread</th><th>Scenario</th><th>Op</th><th>Facility</th><th>Phase</th><th>Terminal outcome</th></tr></thead>\n"
     "      <tbody>\n" (rows scenario-row runs) "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">The append-only decision-fact log, in order, exactly as persisted by "
     "<code>store/append-ledger!</code>. "
     (esc (count ledger)) " facts, of which " (esc (count holds))
     " are HARD governor holds. Note that <code>:pending-approval</code> and <code>:approval-granted</code> "
     "facts are produced on the graph&rsquo;s <code>:audit</code> channel but are never appended by the "
     "<code>:commit</code>/<code>:hold</code> nodes, so they do not appear here &mdash; the persisted trace of an "
     "approval is the <code>approved by</code> column.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Status</th><th>Op</th><th>Facility</th><th>Reason</th><th>Rules fired</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n" (rows ledger-row ledger) "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Committed coordination records</h2>\n"
     "    <p class=\"muted\">What actually landed in the SSoT via <code>store/commit-record!</code>. The approver "
     "column checks each record&rsquo;s own keys and, when attribution is missing there, joins it from the ledger "
     "commit fact instead.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Facility</th><th>Confidence</th><th>Advisor rationale</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n" (rows (partial record-row ledger) records) "\n      </tbody>\n"
     "    </table>\n"
     (when attribution-gap
       (str "    <p class=\"note\"><strong class=\"warn\">Attribution is not retained in the record.</strong> "
            (esc (count attribution-gap))
            " committed record(s) above carry no approver key of their own. <code>operation</code>&rsquo;s "
            "<code>:commit</code> node calls <code>(store/commit-record! store proposal)</code> with the bare "
            "proposal and discards the <code>:approval</code> channel, so the coordination log cannot say who "
            "authorised the commit; the ledger commit fact can, via <code>:approved-by</code>. The column above is "
            "labelled rather than left blank, because a blank cell cannot be told apart from &ldquo;nobody approved "
            "it&rdquo;. The check is re-derived every build and will report attribution normally once the commit "
            "node passes the approval through.</p>\n"))
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-932 &middot; ISIC Rev.4 Division 93 &mdash; other amusement and recreation "
     "activities. Generated from the real actor; no figure on this page was typed by hand. This actor never "
     "performs ride-safety-inspection sign-offs, pricing or programming policy, operational-readiness "
     "decisions, or safety-authority overrides.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ------------------------------- main -------------------------------

(def ^:private date-leak-re
  "A `java.util.Date` rendered with `str` starts with a three-letter day
  name. If one of these reaches the document, the page is no longer
  reproducible, so the build refuses it."
  #"(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{2}")

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (governor-holds ledger)
        html (render result)]

    ;; Build-time invariant: a console that cannot show the governor
    ;; refusing something is not evidence that the governor works.
    (when (empty? holds)
      (throw (ex-info "refusing to write a console with 0 HARD governor holds"
                      {:ledger-facts (count ledger)
                       :holds 0
                       :statuses (frequencies (map :status ledger))})))

    ;; Build-time invariant: no timestamp may reach the page.
    (when-let [leak (re-find date-leak-re html)]
      (throw (ex-info "refusing to write a non-reproducible console: a timestamp leaked"
                      {:match leak})))

    (when-let [dir (.getParentFile (java.io.File. ^String out))]
      (.mkdirs dir))
    (spit out html)
    (println (format "wrote %s (%d bytes)" out (count (.getBytes ^String html "UTF-8"))))
    (println (format "  %d ledger facts, %d HARD governor holds, %d committed records"
                     (count ledger) (count holds) (count (store/coordination-log db))))
    (println (format "  rules fired: %s"
                     (str/join ", " (sort (map name (distinct (mapcat rules-fired holds)))))))))
