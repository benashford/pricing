(ns pricing.engine-test
  (:use pricing.engine
        midje.sweet))

;; substitute-accessors
;;
(facts "about substitute-accessors"
  (substitute-accessors :blah) => :blah
  (binding [out {:blah 30}]
    (substitute-accessors :blah) => 30
    (substitute-accessors (+ :blah 1)) => 31
    (substitute-accessors (+ (+ :blah 1) 1)) => 32))

;; attr
;;
(defn step-equals [fns value]
  (let [result ((last fns))]
    (= result value)))

(facts "about attr"
  (binding [steps (atom [])
            out {:blah 30}]
    (attr :test 12) => #(step-equals % {:test 12})
    (attr :test (+ 1 2)) => #(step-equals % {:test 3})
    (attr :test (+ :blah 2)) => #(step-equals % {:test 32})))

;; table
;;
(facts "about table"
  (binding [lookups (atom {})]
    (table :stuff [1 2])
    (@lookups :stuff) => {1 2}
    (@lookups :nonsuch) => nil))

;; lookup
;;
(facts "about lookup"
  (binding [lookups {:stuff {1 2}}]
    (lookup :stuff 1) => 2
    (lookup :stuff 2) => nil))

;; item
;;
(facts "about item"
  (binding [steps (atom [])
            out {:blah 30}]
    (item :things
      (attr :stuff 12)) => #(step-equals % {:things {:stuff 12}})
    (item :things
      (attr :stuff (+ 1 :blah))) => #(step-equals % {:things {:stuff 31}})
    (item :things
      (attr :stuff 12)
      (item :other-things
        (attr :other-stuff (+ 1 :stuff :blah)))) => #(step-equals % {:things {:stuff 12 :other-things {:other-stuff 43}}})))

;; per-item
;;
(facts "about per-item"
  (binding [out {:thing {:total 12} :stuff {:total 30}}]
    (per-item + :total) => 42
    (per-item min :total) => 12
    (per-item max :total) => 30))
