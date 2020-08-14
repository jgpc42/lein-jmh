(ns ^:internal jmh.task
  "Fns to be evaluated in a Leiningen subprocess."
  (:require [jmh.core :as jmh]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.pprint :as pprint :refer [pprint]])
  (:import [java.nio.file Files Path Paths]
           [org.openjdk.jmh.util ScoreFormatter]))

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

(defn merge-recursively
  "Merge two like-values recursively with an appropriate combining fn.
  If both values are not maps, sets, or collections, return the second."
  [a b]
  (cond
    (and (map? a) (map? b))
    (merge-with merge-recursively a b)
    (and (set? a) (set? b))
    (set/union a b)
    (and (coll? a) (coll? b))
    (concat a b)
    :else
    b))

(defn- read-file
  "Read the given jmh environment file as edn data."
  [f]
  (try
    (-> f slurp edn/read-string)
    (catch Exception e
      (condp = (.getMessage e)
        "No dispatch macro for: ("
        (throw (RuntimeException.
                (str "edn format does not support the '#(...)' "
                     "anonymous function reader macro")
                e))
        "No dispatch macro for: \""
        (throw (RuntimeException.
                (str "edn format does not support the '#\"...\"' "
                     "regex reader macro")
                e))
        (throw e)))))

(defn glob
  "Return a File seq of paths matching the wildcard pattern."
  [opts ^String wildcard]
  (when wildcard
    (let [root (:jmh.task/root opts ".")
          paths (Paths/get root (into-array String []))]
      (->> (Files/newDirectoryStream paths wildcard)
           (map #(.toFile ^Path %))))))

(def ^{:dynamic true
       :doc "The default :files that provide the benchmark environment."}
  *files* ["jmh.edn" [:resource "jmh.edn"]])

(defn merge-environment
  "Return the jmh environment map."
  [opts]
  (let [file (:file opts (:files opts *files*))
        files (if (and (coll? file) (not (keyword? (first file))))
                file
                [file])
        root (:jmh.task/root opts ".")
        files (mapcat
               (fn [x]
                 (cond
                   (string? x)
                   (let [f (io/file root x)]
                     (when (.exists f)
                       [f]))
                   (and (vector? x) (= (first x) :resource))
                   (when-let [resx (io/resource (second x))]
                     [resx])
                   (and (vector? x) (= (first x) :glob))
                   (glob opts (second x))
                   :else
                   nil))
               files)
        envs (map read-file files)]
    (reduce merge-recursively nil
            (concat envs [(:jmh.task/env opts {})]))))

(defn normalize-options
  "Return the merged options map for the task options."
  [opts]
  (if (:pprint opts)
    (assoc opts :format :pprint)
    opts))

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
      (let [eta (long eta)
            pct (double pct)
            line (format "{:%% %.1f :eta \"%02d:%02d:%02d\"}"
                         (* 100.0 pct)
                         (long (/ eta (* 60.0 60)))
                         (long (/ (double (mod eta (* 60.0 60))) 60))
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
  (let [fmts (keyword-seq (:format opts ::default))
        defs (apply merge (for [f fmts] (get format-options f)))
        opts (merge defs opts {:format fmts})
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
                 (doseq [kind (:format opts)]
                   (println)
                   (report kind result)))]
    (if (string? dest)
      (spit dest output)
      (binding [*out* (if (= :err dest) *err* *out*)]
        (print output)
        (flush)))))

(defn list-profilers
  "Print the available profilers as a table."
  []
  (let [rows (->> (jmh/profilers)
                  (filter :supported)
                  (sort-by :name))]
    (println (format-table [:name :desc] rows))))

(defn show-help
  "Print a general help message"
  []
  (println
   "Arguments: ([options-or-keyword] [])

  By default, a 'jmh.edn' file is used to specify the benchmark
  environment. It may be found in the current directory and/or available
  as a resource. This can be altered via the :file option, below.

  Normally, a jmh-clojure options map is provided as the task argument,
  in this case, the following options are recognized or overridden by
  this task runner:

  :exclude   the keys to remove of each result map. Overridden by :only.

  :file      specify another file to read instead of 'jmh.edn'. Can also
             be a tuple of [:glob \"pattern\"] or [:resource \"path\"].
             The ordering of globbed files may be undefined. See :files.

  :files     synonym for :file. A sequence of :file values. Each file's
             data is recursively merged from left to right.

  :format    keyword or sequence of keywords (for multiple outputs).
             See below.

  :only      the keys to select of each result map. Overrides :exclude.

  :output    by default the :format results are written to stdout. If
             :err, the results are instead written to stderr. If a
             string is provided, write to the specified file.

  :progress  if true or :out, report progress to stdout, if :err report
             to stderr. If false, ignore. Defaults to :out.

  :pprint    equivalent to :format :pprint.

  :sort      key or key seq. Sort results by the given keys. Each key
             may also be a tuple of [key order], where order is either
             :desc or :asc (default).

  Available output formats:

  :pprint  pretty print results via `clojure.pprint/pprint`.

  :table   tabular format. Results with nested maps (e.g., :profilers)
           are expanded. Due to width constraints, this format elides
           some information present in other formats. This format also
           excludes some keys automatically. Specify an empty collection
           to the :exclude option to show all columns.

  Instead of an options map, the following command keywords are available:

  :profilers  data about available profilers.
  :help       show this help message.

  Please see the jmh-clojure project for more information on
  configuration and options."))

(defn main
  "Run a jmh task. Accepts an optional command keyword or option map."
  ([] (main nil))
  ([task-arg] (main task-arg {}))
  ([task-arg extra-opts]
   (condp = task-arg
     :profilers (list-profilers)
     :help (show-help)
     (let [opts (-> (merge task-arg extra-opts)
                    normalize-options)
           env (merge-environment opts)]
       (run-benchmarks env opts)))))

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
  expand-keys [:percentiles :secondary])

(defn pr-str-max
  "Convert `val` to string via `pr-str`. If the string is longer than
  `max-width`, truncate the middle and replace with `replacement`."
  ([val] (pr-str-max val 48 " ... "))
  ([val ^long max-width replacement]
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
        max (long (apply max 0 vals))]
    (for [m rows]
      (if-let [[a b] (col-key m)]
        (let [nspace (+ (long sep-width) (- max (count a)))
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
                       (some-update :score-error format-score-error))))))]
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
