(ns pricing.core-test
  (:use pricing.core
        midje.sweet))

; These test the sample models in the pricing.core namespace

(fact (sample {:full-time-employees 4
               :part-time-employees 3
               :company-type "partnership"}) =>
               {:status :quote
                :number-of-employees 7
                :breakdown {:support {:standard-discount 0.75M
                                      :unit-cost 7.5M
                                      :total-before-apportionment 39.38M
                                      :total 39.38M}
                            :licensing {:unit-cost 900.0M
                                        :total-before-apportionment 6300.00M
                                        :total 6300.00M}
                            :total-apportionment-factor 1M
                            :total 6339.38M}
                :company-type-discount 10.0M
                :total 6329.38M})

(fact (sample {:full-time-employees 50
               :part-time-employees 51}) =>
               {:status :noquote
                :reason "No such key: 101 in table: :support-prices"})

(fact (sample {:full-time-employees 1
               :part-time-employees 0
               :company-type "limited"}) =>
               {:status :quote
                :number-of-employees 1
                :breakdown {:support {:standard-discount 0.75M
                                      :unit-cost 10.0M
                                      :total-before-apportionment 7.50M
                                      :total 18.61M}
                            :licensing {:unit-cost 1000.0M
                                        :total-before-apportionment 1000.00M
                                        :total 2481.39M}
                            :total-apportionment-factor 2.48138957816M
                            :total 2500.00M}
                :company-type-discount 0.0M
                :total 2500.00M})
                