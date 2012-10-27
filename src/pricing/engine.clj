(ns pricing.engine)

(def ^:dynamic steps nil)
(def ^:dynamic lookups nil)

(def ^:dynamic in nil)
(def ^:dynamic out nil)

(defn walk-int [exprs pred callback]
  (let [pred-f (resolve pred)
        callback-f (eval callback)
        walk (fn [item]
               (cond
                (pred-f item) (callback-f item)
                (seq? item) (walk-int item pred callback)
                (vector? item) (walk-int item pred callback)
                (map? item) (walk-int item pred callback)
                :else item))]
    (cond
     (seq? exprs) (map walk exprs)
     (vector? exprs) (mapv walk exprs)
     (map? exprs) (into {} (map (fn [[k v]] [k (walk v)]) exprs))
     :else (walk exprs))))

(defmacro walker [exprs pred callback]
  (walk-int exprs pred callback))

(defmacro substitute-accessors [exprs]
  `(walker ~exprs keyword? (fn [item#]
                             `(if (contains? out ~item#)
                                (out ~item#)
                                ~item#))))

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

(defn to-bigdec-int [exprs]
  (letfn [(wrap [item]
            (cond
             (float? item) (bigdec item)
             (seq? item) (to-bigdec-int item)
             (vector? item) (to-bigdec-int item)
             (map? item) (to-bigdec-int item)
             :else item))]
    (cond
     (seq? exprs) (map wrap exprs)
     (vector? exprs) (mapv wrap exprs)
     (map? exprs) (into {} (map (fn [[k v]] [k (wrap v)]) exprs))
     :else (wrap exprs))))

(defmacro to-bigdec [exprs]
  (to-bigdec-int exprs))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])
             lookups (atom {})]
     (to-bigdec
      (do
        ~@body))
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
