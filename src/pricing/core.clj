(ns pricing.core
  (:use pricing.engine))

(defmodel sample
  (attr :number-of-employees (+ (in :full-time-employees) (in :part-time-employees)))

  (item :support
        (attr :standard-discount 0.75)
        (attr :unit-cost (lookup :support-prices :number-of-employees))
        (attr :total (* :unit-cost :number-of-employees :standard-discount)))

  (item :licensing
        (attr :unit-cost (lookup :licence-prices :number-of-employees))
        (attr :total (* :unit-cost :number-of-employees)))

  (attr :total-before-discount (per-item + :total))

  (attr :company-type-discount (lookup :company-type-discount (in :company-type)))
  
  (attr :total (- :total-before-discount :company-type-discount))

  (range-table :support-prices
         [1 10.0]
         [2 9.5]
         [3 9]
         [5 8]
         [6 7.5]
         [10 6]
         [100 :stop])

  (range-table :licence-prices
         [1 1000.0]
         [5 950.0]
         [7 900.0]
         [8 850.0]
         [9 800.0]
         [10 750.0]
         [100 500.0]
         [1000 250.0])

  (table :company-type-discount
         ["limited" 0.0]
         ["partnership" 10.0]))
