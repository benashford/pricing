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

  (attr :total (per-item + :total))

  (table :support-prices
         [1 10.0]
         [2 9.5]
         [3 9]
         [4 9]
         [5 8]
         [6 7.5]
         [7 7.5]
         [8 7.5]
         [9 7.5]
         [10 7.5])

  (table :licence-prices
         [1 1000.0]
         [2 1000.0]
         [3 1000.0]
         [4 1000.0]
         [5 950.0]
         [6 950.0]
         [7 900.0]
         [8 850.0]
         [9 800.0]
         [10 750.0]))
