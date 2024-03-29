(defproject lein-jmh "0.3.1-SNAPSHOT"
  :description "Run jmh-clojure benchmarks with Leiningen."
  :url "https://github.com/jgpc42/lein-jmh"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]]

  :eval-in :leiningen
  :min-lein-version "2.0.0"

  :aliases {"test-all" ["do" "test,"
                        "with-profile" "+1.10" "test,"
                        "with-profile" "+1.9" "test,"
                        "with-profile" "+1.7" "test"]}

  :profiles
  {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}})
