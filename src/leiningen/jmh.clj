(ns leiningen.jmh
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.jmh.form :as form]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project]))

(def ^:private version
  (-> "version.edn" io/resource slurp edn/read-string))

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
  (let [path (:compile-path project (or *compile-path* "classes"))
        opts (if (:pprint opts)
               (assoc opts :format :pprint)
               opts)]
    (merge {:compile-path path} opts)))

(defn jmh
  "Benchmark with JMH, the Java Microbenchmark Harness.

A 'jmh.edn' file at the root of the project is used to configure
lein-jmh. Alternately, or additionally, a :jmh key in project.clj can
also be used. If both are present, the key value is recursively merged
after the data in the file.

The :jmh profile is automatically merged when running this task.

A jmh options map may be provided as the task argument. Additionally,
the following options are recognized by this task:

  :exclude   the keys to remove of each result map.

  :file      specify another file to read instead of 'jmh.edn'.

  :format    keyword. See below.

  :only      the keys to select of each result map.

  :output    by default the results are written to stdout. If :err,
             the results are instead written to stderr. If a string is
             provided, write the result to the specified file.

  :progress  if true or :out, report progress to stdout, if :err report
             to stderr. If false, ignore. Defaults to :out.

  :pprint    Equivalent to :format :pprint.

  :sort      key or key seq. Sort results by the given keys. Each key
             may also be a tuple of [key order], where order is either
             :desc or :asc (default).

Available output formats:

  :pprint  pretty print results via `clojure.pprint`.
  :table   tabular format. Results with nested maps (e.g., :profilers)
           are expanded.

To return data about available profilers, the single keyword :profilers
may be given in place of the options map.

Please see the jmh-clojure project for more information on
configuration and options."
  ([project]
   (jmh project "nil"))
  ([project options-or-keyword]
   (let [task-arg (read-string options-or-keyword)

         project (if (-> project meta :profiles :jmh)
                   (project/merge-profiles project [:jmh])
                   project)

         deps [['jmh-clojure (:jmh-clojure version)]]
         project (project/merge-profiles project [{:dependencies deps}])

         form (if (= :profilers task-arg)
                (let [rows `(->> (jmh.core/profilers)
                                 (filter :supported)
                                 (sort-by :name))]
                  (form/print-table [:name :desc] rows))
                (let [opts (merge-options project task-arg)
                      file (:file opts "jmh.edn")
                      env (merge-environment project file)]
                  (when-not (or (seq (:benchmarks env))
                                (seq (:externs opts)))
                    (main/abort "No benchmarks found"))
                  (form/run-benchmarks env opts)))]

     (eval/eval-in-project project form form/init))))
