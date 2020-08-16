(defproject jmh-clojure/task "0.1.0-SNAPSHOT"
  :description "Various jmh-clojure file and output utilities."
  :url "https://github.com/jgpc42/lein-jmh/tree/master/task"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:dir ".."}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [jmh-clojure "0.4.0-SNAPSHOT"]]

  :min-lein-version "2.0.0"

  :aliases {"test-all" ["do" "test,"
                        "with-profile" "+1.10" "test,"
                        "with-profile" "+1.9" "test,"
                        "with-profile" "+1.7" "test"]}

  :profiles
  {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}})
