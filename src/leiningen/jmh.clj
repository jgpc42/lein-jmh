(ns leiningen.jmh
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project]))

(def ^:private version
  (-> "version.edn" io/resource slurp edn/read-string))

(def ^:private task-requires
  '[jmh.core clojure.pprint])

(def ^:private init-form
  (list* `require (map (partial list 'quote)
                       task-requires)))

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

(defn merge-environment
  "Return the jmh environment map for the given project."
  [project env-file]
  (let [file (io/file (:root project) env-file)]
    (merge-recursively
     (when (.exists file)
       (-> file slurp read-string))
     (:jmh project {}))))

(defn merge-options
  "Return the merged options map for the given project and task options."
  [project opts]
  (let [path (:compile-path project (or *compile-path* "classes"))]
    (merge {:compile-path path} opts)))

(def ^{:private true
       :doc "The code for a fn that reports progress events to the given
            Writer object."}
  progress-form
  `(let [prev# (volatile! nil)]
     (fn [out# {eta# :eta, pct# :percent}]
       (let [line# (format "{:%% %.1f :eta \"%02d:%02d:%02d\"}"
                           (* 100.0 pct#)
                           (long (/ eta# (* 60.0 60)))
                           (long (/ (mod eta# (* 60.0 60)) 60))
                           (mod eta# 60))
             nline# (count line#)
             nprev# (count @prev#)
             prev?# (pos? nprev#)
             clear# (if (and prev?# (< nline# nprev#))
                      (apply str (repeat (- nprev# nline#) \space))
                      "")]
         (binding [*out* out#]
           (locking out#
             (when prev?#
               (print \return))
             (print line#)
             (print clear#)
             (when (>= pct# 1.0)
               (newline))
             (flush)
             (vreset! prev# line#)))))))

(defn- options-form
  "Return the code for the benchmark options map."
  [opts]
  `(let [opts# '~opts
         pval# (:progress opts# :out)]
     (cond
       (or (#{:out :err} pval#)
           (true? pval#))
       (assoc opts# :progress
              (partial ~progress-form
                       (if (= pval# :err) *err* *out*)))
       (not pval#)
       opts#
       :else
       (update opts# :progress eval))))

(defn- run-form
  "Return the benchmarking code."
  [project {out :output :as opts}]
  (let [env (merge-environment project (:file opts "jmh.edn"))
        opts (merge-options project opts)
        write (if (:pprint opts) 'clojure.pprint/pprint `prn)
        result (gensym "result")
        run-form `(jmh.core/run '~env ~(options-form opts))]

    (when-not (seq (:benchmarks env))
      (main/abort "No benchmarks found"))

    `(try
       (let [~result ~(if (:only opts)
                        `(for [m# ~run-form]
                           (select-keys m# '~(:only opts)))
                        run-form)]
         ~(if (string? out)
            `(spit ~out (with-out-str (~write ~result)))
            `(binding [*out* ~(if (= :err out) `*err* `*out*)]
               (~write ~result))))
       (catch Exception e#
         (.printStackTrace e#)
         (System/exit 1)))))

(def ^{:private true
       :doc "The profiling report code."}
  profilers-form
  `(let [profilers# (->> (jmh.core/profilers)
                         (filter :supported)
                         (sort-by :name))
         keys# [:name :desc]
         widths# (for [k# keys#
                       :let [lens# (map (comp count str k#)
                                        profilers#)]]
                   (apply max (cons (count (str k#)) lens#)))
         fmts# (map #(format "~%da" %) widths#)
         fmt# (str "~:{~%"
                   (apply str (interpose "  " fmts#))
                   "~}")
         seps# (for [w# widths#]
                 (apply str (repeat w# \-)))
         header# [keys# seps#]]
     (->> profilers#
          (map (apply juxt keys#))
          (concat header#)
          (clojure.pprint/cl-format true fmt#))))

(defn jmh
  "Benchmark with JMH, the Java Microbenchmark Harness.

A 'jmh.edn' file at the root of the project is used to configure
lein-jmh. Alternately, or additionally, a :jmh key in project.clj can
also be used. If both are present, the key value is recursively merged
after the data in the file.

The :jmh profile is automatically merged when running this task.

A jmh options map may be provided as the task argument. Additionally,
the following options are recognized by this task:

  :file      specify another file to read instead of 'jmh.edn'.
  :only      the keys to select of each result map.
  :output    by default the results are written to stdout. If :err,
             the results are instead written to stderr. If a string is
             provided, write the result to the specified file.
  :progress  if true or :out, report progress to stdout, if :err report
             to stderr. Defaults to :out.
  :pprint    if true, pretty print the results.

To return data about available profilers, the single keyword :profilers
may be given in place of the options map.

Please see the jmh-clojure project for more information on
configuration and options."
  ([project]
   (jmh project ""))
  ([project options-or-keyword]
   (let [task-arg (read-string options-or-keyword)

         project (if (-> project meta :profiles :jmh)
                   (project/merge-profiles project [:jmh])
                   project)

         deps [['jmh-clojure (:jmh-clojure version)]]
         project (project/merge-profiles project [{:dependencies deps}])

         form (if (= :profilers task-arg)
                profilers-form
                (run-form project task-arg))]

     (eval/eval-in-project project form init-form))))
