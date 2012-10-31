(ns pricing.engine)

(def ^:dynamic steps nil)
(def ^:dynamic lookups nil)

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

(defprotocol NoQuote
  (message [msg]))

(defn no-quote [msg]
  (throw (proxy [Exception pricing.engine.NoQuote] [] (message [] msg))))

(defn no-quote? [e]
  (satisfies? NoQuote e))

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
  `(let [lookup-table# (into {} '~data)]
     (swap! lookups assoc ~table-name
            (fn [key#]
              (if (contains? lookup-table# key#)
                (lookup-table# key#)
                (no-quote (str "No such key: " key# " in table: " ~table-name)))))))

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
               :else (no-quote (str "No such key: " ~key-param " in table: " ~table-name)))))))

(defn lookup [table-name key]
  (let [lookup-fn (lookups table-name)]
    (lookup-fn key)))

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

(def minimum-of max)

(defn per-item
  ([key f] (per-item key f identity))
  ([key f f2 & f2-args]
     (->>
      out
      vals
      (filter map?)
      (map key)
      (reduce f)
      (conj (vec f2-args))
      (apply f2))))

(defmacro to-bigdec [exprs]
  `(walker ~exprs float? bigdec))

(defmacro defmodel [modelname & body]
  `(binding [steps (atom [])
             lookups (atom {})]
     (to-bigdec
      (do
        ~@body))
     (let [steps-int# @steps
           lookups-int# @lookups]
       (defn ~modelname [in#]
         (with-precision 12
           (try
             (let [out-atom# (atom {:status :quote})]
               (doseq [step# steps-int#]
                 (binding [in in#
                           out @out-atom#
                           lookups lookups-int#]
                   (swap! out-atom# merge (step#))))
               @out-atom#)
             (catch Exception e#
               (if (no-quote? e#)
                 {:status :noquote :reason (message e#)}
                 (throw e#)))))))))
