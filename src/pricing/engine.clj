(ns pricing.engine)

(def ^:dynamic steps nil)
(def ^:dynamic lookups nil)

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
            (let [result# (substitute-accessors ~value)]
              {'~name result#}))))

(defmacro table [table-name & data]
  `(swap! lookups assoc ~table-name (into {} '~data)))

(defn lookup [table-name key]
  ((lookups table-name) key))

(defmacro item [item-name & body]
  `(let [inner-steps# (atom [])]
     (binding [steps inner-steps#]
       ~@body)
     (swap! steps conj
            (fn []
              (let [outer-out# out
                    out-atom# (atom {})]
                (doseq [step# @inner-steps#]
                  (binding [out (merge @out-atom# outer-out#)]
                    (swap! out-atom# merge (step#))))
                {'~item-name @out-atom#})))))

(defn per-item [f key]
  (->>
   out
   vals
   (filter map?)
   (map key)
   (reduce f)))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])
             lookups (atom {})]
     ~@body
     (let [steps-int# @steps
           lookups-int# @lookups]
       (defn ~modelname [in#]
         (let [out-atom# (atom {})]
           (doseq [step# steps-int#]
             (binding [in in#
                       out @out-atom#
                       lookups lookups-int#]
               (swap! out-atom# merge (step#))))
           @out-atom#)))))
