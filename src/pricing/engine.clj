(ns pricing.engine)

(def ^:dynamic steps nil)

(def ^:dynamic in nil)
(def ^:dynamic out nil)

(defmacro substitute-accessors [exprs]
  (letfn [(substitute [item]
            (cond
              (keyword? item) `(if (contains? out '~item)
                                 (out '~item)
                                 ~item)
              (seq? item) `(substitute-accessors ~item)
              :else item))]
    (if (seq? exprs)
      (map substitute exprs)
      (substitute exprs))))

(defmacro attr [name value]
  `(swap! steps conj
    (fn []
      {'~name (substitute-accessors ~value)})))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])]
    ~@body
    (let [steps-int# @steps]
      (defn ~modelname [in#]
        (let [out-atom# (atom {})]
          (doseq [step# steps-int#]
            (binding [in in#
                      out @out-atom#]
              (swap! out-atom# merge (step#))))
          @out-atom#)))))
