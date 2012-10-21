(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

(fact (sample {}) =>
  {:total 100
   :tax-rate 0.2
   :total-inc-tax 120.0})

(fact (sample2 {:full-time-employees 2 :part-time-employees 1}) =>
  {:number-of-employees 3
   :price-per-employee 9
   :total 27})
