(ns leiningen.test.jmh
  (:require [leiningen.jmh :as jmh]
            [leiningen.test-util :as util]
            [clojure.edn :as edn]
            [clojure.test :refer :all]))

(deftest ^:integration test-jmh
  (let [temp (doto (java.io.File/createTempFile "temp" ".edn")
               .deleteOnExit)

        opts {:output (str temp)
              :status true
              :warnings false}

        {out :out, err :err} (util/run-task-in-project "sample-project" jmh/jmh [opts])
        result (.trim (slurp temp))]

    #_(print result)
    (print err)

    (is (re-find #"(?m)^# JMH version: [.\d]+$"
                 out))

    (is (re-find #"(?m)^# Run complete. Total time: [:\d]+$"
                 out))

    (is (= [\( \{]
           (take 2 result))
        (str "invalid output:\n" (pr-str result)))

    (is (= [0 1]
           (->> result edn/read-string (map :index))))))
