(ns pricing.engine)

(def ^:dynamic steps nil)

(defmacro substitute-accessors [out exprs]
  (letfn [(substitute [item]
            (cond
              (keyword? item) `(if (contains? ~out '~item)
                                 (~out '~item)
                                 ~item)
              (seq? item) `(substitute-accessors ~out ~item)
              :else item))]
    (if (seq? exprs)
      (map substitute exprs)
      (substitute exprs))))

(defmacro attr [name value]
  `(swap! steps conj
    (fn [in# out#]
      {'~name (substitute-accessors out# ~value)})))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])]
    ~@body
    (let [steps-int# @steps]
      (defn ~modelname [in#]
        (let [out# (atom {})]
          (doseq [step# steps-int#]
            (swap! out# merge (step# in# @out#)))
          @out#)))))
