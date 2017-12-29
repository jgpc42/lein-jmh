(ns ^:internal jmh.task
  "Fns to be evaluated in a Leiningen subprocess."
  (:require [jmh.core :as jmh]
            [clojure.pprint :as pprint :refer [pprint]])
  (:import [org.openjdk.jmh.util ScoreFormatter]))

(defmulti report
  "Write the given benchmark results in the specified format."
  (fn [format result] format)
  :default ::default)

(defn- format-score [v]
  (ScoreFormatter/format v))

(defn- format-score-error [v]
  (ScoreFormatter/formatError v))

(defn- keyword-seq
  "Coerce value to a sequence of keywords."
  [x]
  (seq (if (keyword? x) [x] x)))

(defn- some-update
  "Update the given key in the map if it is truthy."
  [m k f & args]
  (apply update m k #(and % (apply f % %&)), args))

;;;

(defn format-table
  "Align the given column key values of the rows in a tabular format.
  Returns a string."
  [cols rows]
  (let [cols (filter #(some % rows) cols)

        widths (for [k cols]
                 (let [lens (map (comp count str k) rows)]
                   (apply max (cons (count (str k)) lens))))

        fmts (map #(format "~%da" %) widths)
        fmt (str "~:{~&"
                 (apply str (interpose "  " fmts))
                 "~}")

        seps (for [w widths]
                (apply str (repeat w \-)))
        header [cols seps]]

    (if (seq cols)
      (->> rows
           (map (apply juxt cols))
           (map #(replace {nil ""} %))
           (concat header)
           (pprint/cl-format nil fmt))
      "")))

(defn progress-reporter
  "Return a fn that will write events to the given Writer object."
  [out]
  (let [prev (volatile! nil)]
    (fn [{eta :eta, pct :percent}]
      (let [line (format "{:%% %.1f :eta \"%02d:%02d:%02d\"}"
                         (* 100.0 pct)
                         (long (/ eta (* 60.0 60)))
                         (long (/ (mod eta (* 60.0 60)) 60))
                         (mod eta 60))
            nline (count line)
            nprev (count @prev)
            prev? (pos? nprev)
            clear (if (and prev? (< nline nprev))
                    (apply str (repeat (- nprev nline) \space))
                    "")]
        (binding [*out* out]
          (locking out
            (when prev?
              (print \return))
            (print line)
            (print clear)
            (when (>= pct 1.0)
              (newline))
            (flush)
            (vreset! prev line)))))))

(defn result-comparator
  "Return a comparator fn that will sort results by the given keys."
  [xs]
  (let [xs (if (#{:asc :desc} (second xs))
             [xs]
             xs)]
    (fn [a b]
      (reduce
       (fn [n x]
         (let [[k order] (if (keyword? x)
                           [x :asc]
                           x)
               n (if (= order :desc)
                   (compare (k b) (k a))
                   (compare (k a) (k b)))]
           (if (zero? n)
             n
             (reduced n))))
       0 xs))))

;;;

(def ^{:doc "The default options for each format."}
  format-options
  {:table {:exclude [:score-confidence :statistics :threads]}})

(defn finalize-options
  "Return the normalized task option map."
  [opts]
  (let [defs (get format-options (:format opts))
        opts (merge defs opts)
        p (:progress opts :out)
        f (if (or (#{:out :err} p)
                  (true? p))
            (progress-reporter (if (= p :err) *err* *out*))
            (eval p))]
    (-> (assoc opts :progress f)
        (update :exclude (comp set keyword-seq))
        (update :only keyword-seq)
        (update :sort keyword-seq))))

(defn prepare-result
  "Sort and select result keys."
  [result opts]
  (cond->> result
    (:sort opts)
    (sort (result-comparator (:sort opts)))
    (:only opts)
    (map #(select-keys % (:only opts)))
    (not (:only opts))
    (map #(select-keys % (remove (:exclude opts) (keys %))))))

(defn run-benchmarks
  "Run the given benchmark environment and options."
  [env {dest :output :as opts}]
  (let [opts (finalize-options opts)

        result (try
                 (jmh/run env opts)
                 (catch Exception e
                   (.printStackTrace e)
                   (System/exit 1)))

        result (prepare-result result opts)

        output (with-out-str
                 (report (:format opts) result))]

    (if (string? dest)
      (spit dest output)
      (binding [*out* (if (= :err dest) *err* *out*)]
        (print output)))))

(defn list-profilers
  "Print the available profilers as a table."
  []
  (let [rows (->> (jmh/profilers)
                  (filter :supported)
                  (sort-by :name))]
    (println (format-table [:name :desc] rows))))

;;;

(defmethod report ::default [_ result]
  (prn result))

(defmethod report :pprint [_ result]
  (pprint result))

;;;

(def ^{:private true
       :doc "The common table column for :fn and :method keys."}
  combine-key :benchmark)

(def ^{:private true
       :doc "The result keys that should be expanded."}
  expand-keys [:percentiles :profilers])

(defn pr-str-max
  "Convert `val` to string via `pr-str`. If the string is longer than
  `max-width`, truncate the middle and replace with `replacement`."
  ([val] (pr-str-max val 48 " ... "))
  ([val max-width replacement]
   {:pre [(< (count replacement) max-width)]}
   (let [s (pr-str val)
         len (count s)]
     (if (> len max-width)
       (let [rlen (count replacement)
             off (quot rlen 2)
             mid (quot max-width 2)
             beg (dec (+ (- mid off) (rem max-width 2)))
             end (dec (+ (- len mid) off (rem rlen 2)))]
         (str (subs s 0 beg)
              replacement
              (subs s end len)))
       s))))

(defn align-column
  "Update each row by aligning the given tuple key's values in its
  column: the first to the left and the second to the right."
  [col-key sep-width f1 f2 rows]
  (let [rows (for [m rows]
               (if-let [[a b] (col-key m)]
                 (assoc m col-key [(f1 a) (f2 b)])
                 m))
        vals (map (comp count first col-key) rows)
        max (apply max 0 vals)]
    (for [m rows]
      (if-let [[a b] (col-key m)]
        (let [nspace (+ sep-width (- max (count a)))
              sep (apply str (repeat nspace \space))]
          (assoc m col-key (str a sep b)))
        m))))

(defn format-statistics
  "Return a string representing some of the statistical data."
  [m]
  (format "{:min %s :max %s :stdev %s}"
          (format-score (:min m))
          (format-score (:max m))
          (format-score-error (:stdev m))))

(defn normalize-row
  "Prepare the result map for table display."
  [row]
  (let [b (:fn row (:method row))
        b (if (seq? b) (pr-str-max b) b)]
    (-> (dissoc row :fn :method)
        (assoc combine-key b)
        (some-update :params pr-str-max)
        (some-update :score-error format-score-error)
        (some-update :statistics format-statistics))))

(defn expand-rows
  "Flatten and append nested result map rows."
  [row]
  (let [expand
        (fn [row-key]
          (->> (for [[k v] (row-key row)]
                 (let [b (str "  " k
                              (when (number? k) "%"))]
                   (-> (dissoc v :statistics)
                       (assoc combine-key b)
                       (some-update :score-error format-score-error))))
               (sort-by combine-key)))]
    (-> (apply dissoc row expand-keys)
        (cons (mapcat expand expand-keys)))))

(defmethod report :table [_ result]
  (let [cols [combine-key :name :mode :samples
              :score :score-error :score-confidence
              :threads :params :statistics]

        rows (->> (map normalize-row result)
                  (mapcat expand-rows)
                  (align-column :score 2 format-score identity)
                  (align-column :score-confidence 2 format-score format-score))]

    (println (format-table cols rows))))
