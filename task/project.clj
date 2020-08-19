(def dependencies
  (->> "deps.edn" slurp read-string
       :deps (mapv #(vector (% 0) (:mvn/version (% 1))))
       (into '[[org.clojure/clojure "1.8.0"]])))

(defproject jmh-clojure/task "0.1.1-SNAPSHOT"
  :description "Various jmh-clojure file and output utilities."
  :url "https://github.com/jgpc42/lein-jmh/tree/master/task"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:dir ".."}

  :dependencies ~dependencies

  :min-lein-version "2.0.0"

  :aliases {"test-all" ["do" "test,"
                        "with-profile" "+1.10" "test,"
                        "with-profile" "+1.9" "test,"
                        "with-profile" "+1.7" "test"]}

  :profiles
  {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}})
