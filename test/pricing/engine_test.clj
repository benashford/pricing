(ns pricing.engine-test
  (:use pricing.engine
        midje.sweet))

;; walker
;;
(facts "about walker"
       (walker (+ 1 2) number? (fn [item#] `(+ ~item# 1))) =expands-to=> (+ (clojure.core/+ 1 1) (clojure.core/+ 2 1))
       (walker (+ 1 2) number? (fn [item#] (+ item# 1))) =expands-to=> (+ 2 3)
       (walker (+ 1.0 2.0) float? (fn [item#] (bigdec item#))) =expands-to=> (+ 1.0M 2.0M)
       (walker (+ 1 :blah) keyword? (fn [_] 1)) =expands-to=> (+ 1 1)
       (walker (+ 1 :blah) keyword? (fn [item#] `(~item# {:blah 1}))) =expands-to=> (+ 1 (:blah {:blah 1})))

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
                                 (attr :other-stuff (+ 1 :stuff :blah))))
                     => #(step-equals % {:things {:stuff 12 :other-things {:other-stuff 43}}})))

;; per-item
;;
(facts "about per-item"
       (binding [out {:thing {:total 12} :stuff {:total 30}}]
         (per-item + :total) => 42
         (per-item min :total) => 12
         (per-item max :total) => 30))

;; to-bigdec
;;
(facts "about to-bigdec"
       (to-bigdec 0.5) => 0.5M
       (to-bigdec [0.1 0.2]) => [0.1M 0.2M]
       (to-bigdec {:a 0.3 :b 0.4}) => {:a 0.3M :b 0.4M}
       (to-bigdec (+ 0.3 0.4)) => 0.7M
       (to-bigdec (with-precision 5 (+ 1 (* 0.3 (/ 22.0 7.0))))) => 1.9429M
       (binding [steps (atom [])]
         (to-bigdec (attr :test 12.0)) => #(step-equals % {:test 12.0M})))

;; status
;;
(facts "about status"
       (defmodel good)
       (good {}) => {:status :quote}
       (defmodel fussy
         (attr :noway (no-quote "no chance")))
       (fussy {}) => {:status :noquote :reason "no chance"})