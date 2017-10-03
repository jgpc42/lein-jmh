(ns leiningen.test.jmh
  (:require [leiningen.jmh :as jmh]
            [leiningen.test-util :as util]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer :all]))

(defn run-task [& [arg :as args]]
  (if (map? arg)
    (let [temp (doto (java.io.File/createTempFile "temp" ".edn")
                 .deleteOnExit)
          opts (->> (assoc arg :output (str temp))
                    (merge {:fail-on-error true, :warnings false}))
          streams (util/run-task-in-project "sample-project" jmh/jmh [opts])]
      (assoc streams :result (slurp temp)))
    (util/run-task-in-project "sample-project" jmh/jmh [arg])))

;;;

(deftest test-merge-recursively
  (is (= {:a [1 5], :b {:c 6, :d #{3 7}}, :e 4, :f 8}
         (jmh/merge-recursively
          {:a '(1), :b {:c 2, :d #{3}}, :e 4}
          {:a [5], :b {:c 6, :d #{7}}, :f 8}))))

(deftest ^:integration test-jmh
  (testing "default format"
    (let [{result :result} (run-task {})]
      (is (= [\( \{]
             (take 2 result))
          (str "invalid output:\n" (pr-str result)))
      (is (= [0 1] (->> result edn/read-string (map :index))))))

  (testing "status"
    (let [{out :out} (run-task {:status true})]
      (is (re-find #"(?m)^# JMH version: [.\d]+$"
                   out))
      (is (re-find #"(?m)^# Run complete. Total time: [:\d]+$"
                   out))))

  (testing "profilers"
    (is (re-find #"^:name +:desc +\n----"
                 (:out (run-task :profilers)))))

  (testing "sort"
    (is (= [1 0] (->> (run-task {:sort [:index :desc]})
                      :result edn/read-string (map :index)))))

  (testing "keys"
    (let [all #{:args :samples :fn :index :name
                :mode :params :threads :score}]
      (are [s m] (= s (->> (run-task m) :result edn/read-string
                           (mapcat keys) set))
        all                {}
        (disj all :score)  {:exclude :score}
        #{:score :threads} {:only [:foo :score :threads]}
        #{:threads}        {:exclude [:foo :score], :only [:score :threads]})))

  (testing "table format"
    (is (re-find #"^:fn/method .+\n----"
                 (-> (run-task {:format :table}) :result str/trim)))))
