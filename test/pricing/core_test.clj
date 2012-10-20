(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

(fact (sample {}) =>
  {:total 100
   :tax-rate 0.2
   :total-inc-tax 120.0})
