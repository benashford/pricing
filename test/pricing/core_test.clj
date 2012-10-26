(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

; These test the sample models in the pricing.core namespace

(fact (sample {}) =>
      {:total 100
       :tax-rate 0.2
       :total-inc-tax 120.0})

(fact (sample2 {:full-time-employees 2 :part-time-employees 1}) =>
      {:number-of-employees 3
       :price-per-employee 9
       :total 27})

(fact (sample3 {:full-time-employees 4 :part-time-employees 3}) =>
      {:number-of-employees 7
       :support {:standard-discount 0.75
                 :unit-cost 7.5
                 :total 39.375}
       :licensing {:unit-cost 900.0
                   :total 6300.0}
       :total 6339.375})
