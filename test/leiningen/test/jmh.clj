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

        {out :out} (util/run-task-in-project "sample-project" jmh/jmh [opts])
        result (slurp temp)]

    (is (re-find #"(?m)^# JMH version: [.\d]+$"
                 out))

    (is (re-find #"(?m)^# Run complete. Total time: [:\d]+$"
                 out))

    (is (= [\( \{]
           (take 2 result))
        (str "invalid output:\n" (pr-str result)))

    (is (= [0 1]
           (->> result edn/read-string (map :index))))))
