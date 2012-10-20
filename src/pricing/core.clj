(ns pricing.core
  (:use pricing.engine))

(defmodel sample
  (attr :total 100)
  (attr :tax-rate 0.2)
  (attr :total-inc-tax (* :total (+ :tax-rate 1))))
