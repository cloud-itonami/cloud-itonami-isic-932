(ns amusementfacilityops.store
  "SSoT for the ISIC-932 amusement/recreation facility administrative coordination actor.

  This actor coordinates the back-office operations of amusement parks, arcades,
  and recreational facilities: facility/attraction booking scheduling,
  maintenance-schedule logistics PROPOSAL (administrative only, never safety
  sign-offs), guest-services coordination, supply coordination, and safety-
  concern flagging (equipment hazards, crowd-safety issues).

  It never touches ride-safety-inspection sign-offs, pricing/programming policy,
  operational-readiness decisions, or safety-authority overrides -- see
  `amusementfacilityops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `facilities` directory keyed by `:facility-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss bug).

  A registered/verified facility record must exist before ANY proposal for that
  facility may ever commit or escalate -- `amusementfacilityops.governor`'s
  `facility-unverified-violations` re-derives this from the facility's own
  `:registered?`/`:verified?` fields, never from proposal self-report, the SAME
  'ground truth, not self-report' discipline every sibling actor's own governor uses.

  The ledger stays append-only: which facility a proposal targeted, which operation,
  on what basis, committed/held/escalated and approved by whom is always a query
  over an immutable log.")

(defprotocol Store
  (facility [s facility-id] "Registered facility record, or nil.
    Facility map: {:facility-id .. :name .. :registered? bool :verified? bool}.")
  (all-facilities [s])
  (booking [s booking-id] "Booking record, or nil.")
  (all-bookings [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-facilities [s facilities] "replace/seed the facility directory")
  (with-bookings [s bookings] "replace/seed the booking directory"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained facility and booking directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run offline."
  []
  {:facilities
   {"facility-1" {:facility-id "facility-1" :name "Central Amusement Park"
                  :registered? true :verified? true
                  :address "123 Main St" :attractions-count 12}
    "facility-2" {:facility-id "facility-2" :name "Community Recreation Center"
                  :registered? true :verified? true
                  :address "456 Park Ave" :attractions-count 4}
    "facility-3" {:facility-id "facility-3" :name "Regional Arcade (intake)"
                  :registered? true :verified? false
                  :address "789 Entertainment Blvd" :attractions-count 8}}
   :bookings
   {"booking-1" {:booking-id "booking-1" :facility-id "facility-1"
                 :event-name "Private Group Event" :event-date "2026-07-20"
                 :party-size 50 :status :proposed}
    "booking-2" {:booking-id "booking-2" :facility-id "facility-2"
                 :event-name "Team Building" :event-date "2026-07-21"
                 :party-size 30 :status :committed}}
   :ledger []
   :coordination-log []})

;; ----------------------------- MemStore implementation -----------------------

(deftype MemStore [atom-data]
  Store
  (facility [_s facility-id]
    (get-in @atom-data [:facilities facility-id]))
  (all-facilities [_s]
    (vals (get @atom-data :facilities {})))
  (booking [_s booking-id]
    (get-in @atom-data [:bookings booking-id]))
  (all-bookings [_s]
    (vals (get @atom-data :bookings {})))
  (ledger [_s]
    (get @atom-data :ledger []))
  (coordination-log [_s]
    (get @atom-data :coordination-log []))
  (commit-record! [_s record]
    (swap! atom-data update :coordination-log conj record))
  (append-ledger! [_s fact]
    (swap! atom-data update :ledger conj fact))
  (with-facilities [_s facilities]
    (swap! atom-data assoc :facilities facilities)
    _s)
  (with-bookings [_s bookings]
    (swap! atom-data assoc :bookings bookings)
    _s))

(defn make-store
  "Create a fresh MemStore from demo data (or seeded with custom data)."
  ([]
   (make-store (demo-data)))
  ([data]
   (MemStore. (atom data))))
