(ns pricing.engine-test
  (:use pricing.engine
        midje.sweet))

;; utils
(facts "about fence-panel"
       (fence-panel [0]) => []
       (fence-panel [0 1]) => [[0 1]]
       (fence-panel [0 1 5]) => [[0 1] [1 5]]
       (fence-panel [0 1 5 10 20]) => [[0 1] [1 5] [5 10] [10 20]])

;; no-quote
;;
(facts "about no-quote"
       (try
         (no-quote! "test")
         (catch Exception e
           (pricing-exception? e))) => true
       (pricing-exception? (Exception. "other")) => false)

;; walker
;;
(facts "about walker"
       (walker (+ 1 2) number? (fn [item#] `(+ ~item# 1))) =expands-to=> (+ (clojure.core/+ 1 1) (clojure.core/+ 2 1))
       (walker (+ 1 2) number? (fn [item#] (+ item# 1))) =expands-to=> (+ 2 3)
       (walker (+ 1.0 2.0) float? (fn [item#] (bigdec item#))) =expands-to=> (+ 1.0M 2.0M)
       (walker (+ 1 :blah) keyword? (fn [_] 1)) =expands-to=> (+ 1 1)
       (walker (+ 1 :blah) keyword? (fn [item#] `(~item# {:blah 1}))) =expands-to=> (+ 1 (:blah {:blah 1})))

;; decline
;;
(defn declined? [exp-value]
  (fn [fs]
    (let [f (last fs)
          act-value (try (f) nil
                         (catch Exception e
                           (if (pricing-exception? e)
                             (if (= (exception-type e) :decline)
                               (message e)))))]
      (= act-value exp-value))))
      

(facts "about decline"
       (binding [filters (atom [])
                 in {:a 1 :c 100}]
         (decline [:a > 1] "decline-a") => (declined? nil)
         (decline [:a >= 1] "decline-a") => (declined? "decline-a")
         (decline [:b = 0] "decline-b") => (declined? nil)
         (decline [:c < 50] "decline-c") => (declined? nil)))

;; round
;;
(facts "about round"
       (round 0.333M 2) => 0.33M
       (round 0.666M 2) => 0.67M
       (round 0.33333 2) => 0.33M
       (round 0.66666 2) => 0.67M
       (round 1 2) => 1.00M)

;; apply-rounding
;;
(facts "about apply-rounding"
       (binding [roundings {:stuff 2}]
         (apply-rounding :stuff 0.333M) => 0.33M
         (apply-rounding :stuff 0.666M) => 0.67M
         (apply-rounding :other 0.333M) => 0.333M))

;; rounding
;;
(facts "about rounding"
       (binding [roundings (atom {})]
         (rounding :stuff 2) => #(= (% :stuff) 2)
         (rounding :stuff 2) => #(nil? (% :other))))

;; substitute-accessors
;;
(facts "about substitute-accessors"
       (substitute-accessors :blah) => :blah
       (binding [out {:blah 30}]
         (substitute-accessors :blah) => 30
         (substitute-accessors (+ :blah 1)) => 31
         (substitute-accessors (+ (+ :blah 1) 1)) => 32)
       (binding [out {:blah {:stuff 10}}]
         (substitute-accessors :blah.stuff) => 10
         (substitute-accessors :blah) = {:stuff 10}))

;; attr
;;
(defn step-equals [fns value]
  (let [result ((last fns))]
    (= result value)))

(facts "about attr"
       (binding [steps (atom [])
                 out {:blah 30}
                 roundings {}]
         (attr :test 12) => #(step-equals % {:test 12})
         (attr :test (+ 1 2)) => #(step-equals % {:test 3})
         (attr :test (+ :blah 2)) => #(step-equals % {:test 32})))

;; table & lookup
;;
(defn is-no-quote [wrapped-e msg]
  (try 
    (let [e (.throwable wrapped-e)]
      (if (pricing-exception? e)
        (= msg (message e))))
    (catch Throwable t
      false)))

(facts "about table"
       (binding [lookups (atom {})]
         (table :stuff [1 2])
         (binding [lookups @lookups]
           (lookup :stuff 1) => 2
           (lookup :stuff 2) => #(is-no-quote % "No such key: 2 in table: :stuff"))))

;; range-table
;;
(facts "about range-table"
       (binding [lookups (atom {})]
         (range-table :stuff
                      [0 100]
                      [5 75]
                      [10 50]
                      [100 :stop])
         (binding [lookups @lookups]
           (lookup :stuff -1) => #(is-no-quote % "No such key: -1 in table: :stuff")
           (lookup :stuff 0) => 100
           (lookup :stuff 1) => 100
           (lookup :stuff 5) => 75
           (lookup :stuff 10) => 50
           (lookup :stuff 99) => 50
           (lookup :stuff 134) => #(is-no-quote % "No such key: 134 in table: :stuff")))
       (binding [lookups (atom {})]
         (range-table :stuff
                      [0 100]
                      [5 75]
                      [10 50]
                      [100 25])
         (binding [lookups @lookups]
           (lookup :stuff 25) => 50
           (lookup :stuff 99) => 50
           (lookup :stuff 100) => 25
           (lookup :stuff 101) => 25
           (lookup :stuff 10000012) => 25)))

;; item
;;
(facts "about item"
       (binding [steps (atom [])
                 out {:blah 30}
                 roundings {}]
         (item :things
               (attr :stuff 12)) => #(step-equals % {:things {:stuff 12}})
               (item :things
                     (attr :stuff (+ 1 :blah))) => #(step-equals % {:things {:stuff 31}})
                     (item :things
                           (attr :stuff 12)
                           (item :other-things
                                 (attr :other-stuff (+ 1 :stuff :blah))))
                     => #(step-equals % {:things {:stuff 12 :other-things {:other-stuff 43}}})))

;; ext-keyword
;;
(facts "extend keyword"
       (ext-keyword :a "-b") => :a-b)

;; aggregation
;;
(defn check-aggregation [fns]
  (let [f (last fns)]
    (f out)))

(facts "about aggregation"
       (binding [out {:thing {:total 12} :stuff {:total 30}}
                 aggregations (atom [])
                 roundings {}]
         (aggregation :total +) => #(= ((check-aggregation %) :total) 42)
         (aggregation :total min) => #(= ((check-aggregation %) :total) 12)
         (aggregation :total max) => #(= ((check-aggregation %) :total) 30)
         (aggregation :total + max 84) => #(= (check-aggregation %)
                                              {:total 84
                                               :total-apportionment-factor 2
                                               :thing {:total 24
                                                       :total-before-apportionment 12}
                                               :stuff {:total 60
                                                       :total-before-apportionment 30}})
         (aggregation :total + (fn [& xs]
                                 (/ (reduce + xs)
                                    (count xs))) 10) => #(= ((check-aggregation %) :total) 26)))

;; to-bigdec
;;
(facts "about to-bigdec"
       (to-bigdec 0.5) => 0.5M
       (to-bigdec [0.1 0.2]) => [0.1M 0.2M]
       (to-bigdec {:a 0.3 :b 0.4}) => {:a 0.3M :b 0.4M}
       (to-bigdec (+ 0.3 0.4)) => 0.7M
       (to-bigdec (with-precision 5 (+ 1 (* 0.3 (/ 22.0 7.0))))) => 1.9429M
       (binding [steps (atom [])
                 roundings {}]
         (to-bigdec (attr :test 12.0)) => #(step-equals % {:test 12.0M})))

;; status
;;
(facts "about status"
       (defmodel good)
       (good {}) => {:status :quote}
       (defmodel fussy
         (attr :noway (no-quote! "no chance")))
       (fussy {}) => {:status :noquote :reason "no chance"})
