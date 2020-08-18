(ns leiningen.jmh
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]))

(def ^{:private true
       :doc "The task subprocess dependency versions."}
  version
  (-> "version.edn" io/resource slurp edn/read-string))

;;;

(defn jmh
  "Benchmark with JMH, the Java Microbenchmark Harness.

  By default, a 'jmh.edn' file is used to specify the benchmark
  environment. It can be located at the root of the project and/or
  available as a resource. Alternately, or additionally, a :jmh key in
  project.clj can also be used. If the key is present, its value is
  recursively merged after the data in the file(s).

  The :jmh profile is automatically merged when running this task.

  This plugin is a very thin wrapper for the `jmh-clojure/task` library.
  Please run `lein jmh :help` to view what it provides to this plugin."
  ([project]
   (jmh project "nil"))
  ([project options-or-keyword]
   (let [project (if (-> project meta :profiles :jmh)
                   (project/merge-profiles project [:jmh])
                   project)
         deps [['jmh-clojure/task (:jmh-clojure/task version)]]
         project (project/merge-profiles project [{:dependencies deps}])
         compile-path (:compile-path project (or *compile-path* "classes"))
         task-arg (read-string options-or-keyword)
         extra-opts {:compile-path compile-path
                     :jmh.task/env (:jmh project {})
                     :jmh.task/root (:root project)}]
     (eval/eval-in-project
      project
      `(jmh.task/main '~task-arg '~extra-opts)
      `(require 'jmh.task)))))
