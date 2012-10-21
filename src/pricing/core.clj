(ns pricing.core
  (:use pricing.engine))

(defmodel sample
  (attr :total 100)
  (attr :tax-rate 0.2)
  (attr :total-inc-tax (* :total (+ :tax-rate 1))))

(defmodel sample2
  (attr :number-of-employees (+ (in :full-time-employees) (in :part-time-employees)))
  (attr :price-per-employee (lookup :prices :number-of-employees))
  (attr :total (* :number-of-employees :price-per-employee))

  (table :prices
    [1 10.0]
    [2 9.5]
    [3 9]
    [4 9]
    [5 9]
    [6 8]))
