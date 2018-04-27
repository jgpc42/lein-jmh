(ns leiningen.jmh
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project])
  (:import [java.io PushbackReader StringReader]))

(def ^{:private true
       :doc "The task subprocess dependency versions."}
  version
  (-> "version.edn" io/resource slurp edn/read-string))

(def ^{:private true
       :doc "The task subprocess namespace."}
  task-ns 'jmh.task)

(def ^{:private true
       :doc "The sequence of forms from the task namespace."}
  task-forms
  (let [file (-> (str task-ns) (.replace \. \/)
                 (str ".clj") io/resource slurp)
        reader (PushbackReader. (StringReader. file))]
    (->> (repeat reader)
         (map #(read % false nil))
         (take-while some?)
         doall)))

(def ^{:private true
       :doc "The task subprocess initialization code."}
  init-form
  (let [orig-ns (gensym "orig")]
    `(do (clojure.lang.Namespace/findOrCreate '~task-ns)
         (intern (the-ns '~task-ns) '~orig-ns (ns-name *ns*))
         ~@task-forms
         (in-ns ~orig-ns))))

;;;

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

(defn merge-environment
  "Return the jmh environment map for the given project."
  [project files]
  (let [envs (for [f files
                   :let [f (io/file (:root project) f)]
                   :when (.exists f)]
               (read-file f))]
    (reduce merge-recursively nil
            (concat envs [(:jmh project {})]))))

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

  :exclude   the keys to remove of each result map. Overridden by :only.

  :file      specify another file to read instead of 'jmh.edn'. Can also
             be a sequence of files to read; each being recursively
             merged with the previous. Specifying multiple files can be
             useful, for example, to organize large sets of parameters.

  :files     synonym for :file.

  :format    keyword. See below.

  :only      the keys to select of each result map. Overrides :exclude.

  :output    by default the results are written to stdout. If :err,
             the results are instead written to stderr. If a string is
             provided, write the result to the specified file.

  :progress  if true or :out, report progress to stdout, if :err report
             to stderr. If false, ignore. Defaults to :out.

  :pprint    equivalent to :format :pprint.

  :sort      key or key seq. Sort results by the given keys. Each key
             may also be a tuple of [key order], where order is either
             :desc or :asc (default).

Available output formats:

  :pprint  pretty print results via `clojure.pprint`.

  :table   tabular format. Results with nested maps (e.g., :profilers)
           are expanded. Due to width constraints, this format elides
           some information present in other formats. This format also
           excludes some keys automatically. Specify an empty collection
           to the :exclude option to show all columns.

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
                `(jmh.task/list-profilers)
                (let [opts (merge-options project task-arg)
                      file (:file opts (:files opts "jmh.edn"))
                      files (if (coll? file) file [file])
                      env (merge-environment project files)]
                  (when-not (or (seq (:benchmarks env))
                                (seq (:externs opts)))
                    (main/abort "No benchmarks found"))
                  `(jmh.task/run-benchmarks '~env '~opts)))]

     (eval/eval-in-project project form init-form))))
