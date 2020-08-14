(ns jmh.main
  (:require [jmh.task :as task])
  (:gen-class))

(defn -main [& [arg]]
  (let [arg (case arg
              ("help" "-h" "-help" "--help") ":help"
              arg)]
    (if arg
      (task/main (read-string arg))
      (task/main))))
