(ns leiningen.test.jmh
  (:require [leiningen.jmh :as jmh]
            [leiningen.test-util :as util]
            [clojure.edn :as edn]
            [clojure.test :refer :all]))

(deftest test-merge-recursively
  (is (= {:a [1 5], :b {:c 6, :d #{3 7}}, :e 4, :f 8}
         (jmh/merge-recursively
          {:a '(1), :b {:c 2, :d #{3}}, :e 4}
          {:a [5], :b {:c 6, :d #{7}}, :f 8}))))

(deftest ^:integration test-jmh
  (let [temp (doto (java.io.File/createTempFile "temp" ".edn")
               .deleteOnExit)
        opts {:fail-on-error true
              :output (str temp)
              :status true
              :warnings false}
        result (util/run-task-in-project "sample-project" jmh/jmh [opts])
        output (slurp temp)]

    (is (= [\( \{]
           (take 2 output))
        (str "invalid stderr output:\n" (pr-str output)))

    (is (-> output edn/read-string first :samples))

    (is (re-find #"(?m)^# JMH version: [.\d]+$"
                 (:out result)))
    (is (re-find #"(?m)^# Run complete. Total time: [:\d]+$"
                 (:out result)))))
