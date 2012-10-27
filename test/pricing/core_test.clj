(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

; These test the sample models in the pricing.core namespace

(fact (sample {:full-time-employees 4 :part-time-employees 3}) =>
      {:number-of-employees 7
       :support {:standard-discount 0.75M
                 :unit-cost 7.5M
                 :total 39.375M}
       :licensing {:unit-cost 900.0M
                   :total 6300.0M}
       :total 6339.375M})
