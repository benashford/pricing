(ns pricing.engine
  (:use [clojure.string :only [split]]))

(def ^:dynamic steps nil)
(def ^:dynamic lookups nil)
(def ^:dynamic aggregations nil)
(def ^:dynamic roundings nil)
(def ^:dynamic filters nil)

(def ^:dynamic in nil)
(def ^:dynamic out nil)

;; utilities

(defn fence-panel [posts]
  (loop [[a & others] posts
         panels []]
    (let [b (first others)]
      (if-not b
        panels
        (recur others (conj panels [a b]))))))

;; Error handling

(defprotocol PricingException
  (message [msg])
  (exception-type [t]))

(defn raise-pricing-exception [msg t]
  (throw (proxy [Exception pricing.engine.PricingException] [] 
           (message [] msg)
           (exception_type [] t))))

(defn no-quote! [msg]
  (raise-pricing-exception msg :noquote))

(defn decline! [msg]
  (raise-pricing-exception msg :decline))

(defn pricing-exception? [e]
  (satisfies? PricingException e))

;; Macro support

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

;; Pricing engine

(defn decline [[param-name pred-f value] message]
  (swap! filters conj
         (fn []
           (if (pred-f (in param-name) value)
             (decline! message)))))

(defn round [number places]
  (.setScale (with-precision 12 (bigdec number)) places java.math.BigDecimal/ROUND_HALF_EVEN))

(defmacro apply-rounding [key & exprs]
  `(let [rounding# (roundings ~key)]
     (if rounding#
       (round ~@exprs rounding#)
       ~@exprs)))

(defn rounding [key r]
  (swap! roundings assoc key r))

(defn get-out [key]
  (get-in out (map keyword (split (name key) #"\.")) key))

(defmacro substitute-accessors [exprs]
  `(walker ~exprs keyword? (fn [item#]
                             `(get-out ~item#))))

(defmacro attr [name value]
  `(swap! steps conj
          (fn []
            (let [result# (substitute-accessors ~value)]
              {'~name (apply-rounding ~name result#)}))))

(defmacro table [table-name & data]
  `(let [lookup-table# (into {} '~data)]
     (swap! lookups assoc ~table-name
            (fn [key#]
              (if (contains? lookup-table# key#)
                (lookup-table# key#)
                (no-quote! (str "No such key: " key# " in table: " ~table-name)))))))

(defmacro range-table [table-name & data]
  (let [ranges (map vector (fence-panel (map first data)) (map second data))
        [last-post last-value] (last data)
        key-param (gensym "key")]
    `(swap! lookups assoc ~table-name
            (fn [~key-param]
              (cond
               ~@(apply concat
                        (for [[[start end] value] ranges]
                          `((and (>= ~key-param ~start) (< ~key-param ~end)) ~value)))
               ~@(if-not (= last-value :stop)
                   `((>= ~key-param ~last-post) ~last-value))
               :else (no-quote! (str "No such key: " ~key-param " in table: " ~table-name)))))))

(defn lookup [table-name key]
  (let [lookup-fn (lookups table-name)]
    (lookup-fn key)))

(defmacro item [item-name & body]
  `(let [inner-steps# (atom [])
         inner-aggregations# (atom [])]
     (binding [steps inner-steps#
               aggregations inner-aggregations#]
       ~@body)
     (swap! steps conj
            (fn []
              (let [outer-out# out
                    out-atom# (atom {})]
                (doseq [step# @inner-steps#]
                  (binding [out (merge @out-atom# outer-out#)]
                    (swap! out-atom# merge (step#))))
                (doseq [aggregation# @inner-aggregations#]
                  (swap! out-atom# aggregation#))
                {'~item-name @out-atom#})))))

(def minimum-of max)

(defn ext-keyword [kw extension]
  (keyword (str (name kw) extension)))

(defmacro aggregation [key f & args]
  (let [f2 (if (nil? (first args)) identity (first args))
        f2-args (next args)]
    `(swap! aggregations conj
            (fn [out#]
              (let [items# (filter (comp map? second) out#)
                    before-f2# (->> items#
                                    (map (comp ~key second))
                                    (reduce ~f))
                    after-f2# (apply ~f2 (conj (vec '~f2-args) before-f2#))
                    apportionment-factor# (+ 1 (/ (- after-f2# before-f2#) before-f2#))]
                (merge out#
                       {~key (apply-rounding ~key after-f2#)
                        (ext-keyword ~key "-apportionment-factor") apportionment-factor#}
                       (into {}
                             (map
                              (fn [[k# v#]]
                                (let [current-total# (~key v#)]
                                  [k# (merge v#
                                             {(ext-keyword
                                               ~key
                                               "-before-apportionment") current-total#
                                              ~key (apply-rounding
                                                    ~key
                                                    (*
                                                     current-total#
                                                     apportionment-factor#))})]))
                              items#))))))))

(defmacro to-bigdec [exprs]
  `(walker ~exprs float? bigdec))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])
             lookups (atom {})
             roundings (atom {})
             filters (atom [])]
     (to-bigdec
      (do
        ~@body))
     (let [steps-int# @steps
           lookups-int# @lookups
           roundings-int# @roundings
           filters-int# @filters]
       (defn ~modelname [in#]
         (with-precision 12 :rounding HALF_EVEN
           (try
             (binding [in in#]
               (doseq [filt# filters-int#]
                 (filt#))
               (let [out-atom# (atom {:status :quote})]
                 (doseq [step# steps-int#]
                   (binding [out @out-atom#
                             lookups lookups-int#
                             roundings roundings-int#]
                     (swap! out-atom# merge (step#))))
                 @out-atom#)
               (catch Exception e#
                 (if (pricing-exception? e#)
                   {:status (exception-type e#) 
                    :reason (message e#)}
                   (throw e#))))))))))
