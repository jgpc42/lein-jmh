(def version
  (-> "resources/version.edn" slurp read-string))

(defproject lein-jmh "0.2.4"
  :description "Run jmh-clojure benchmarks with Leiningen."
  :url "https://github.com/jgpc42/lein-jmh"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]]

  :eval-in :leiningen
  :min-lein-version "2.0.0"

  :aliases {"test-all" ["do" "test,"
                        "with-profile" "+1.9" "test,"
                        "with-profile" "+1.7" "test"]}

  :profiles
  {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :dev {:dependencies [[jmh-clojure ~(:jmh-clojure version)]]}
   :repl {:source-paths ["dev"]}})
