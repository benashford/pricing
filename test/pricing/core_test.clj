(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

; These test the sample models in the pricing.core namespace

(fact (sample {:full-time-employees 4
               :part-time-employees 3
               :company-type "partnership"}) =>
      {:status :quote
       :number-of-employees 7
       :support {:standard-discount 0.75M
                 :unit-cost 7.5M
                 :total 39.375M}
       :licensing {:unit-cost 900.0M
                   :total 6300.0M}
       :total-before-discount 6339.375M
       :company-type-discount 10.0M
       :total 6329.375M})

(fact (sample {:full-time-employees 50 :part-time-employees 51}) =>
      {:status :noquote
       :reason "No such key: 101 in table: :support-prices"})