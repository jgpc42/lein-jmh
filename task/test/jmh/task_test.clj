(ns jmh.task-test
  (:require [jmh.task :as task]
            [clojure.test :refer :all]))

(defn lines [s]
  (seq (.split s (System/getProperty "line.separator"))))

(deftest test-merge-recursively
  (is (= {:a [1 5], :b {:c 6, :d #{3 7}}, :e 4, :f 8}
         (task/merge-recursively
          {:a '(1), :b {:c 2, :d #{3}}, :e 4}
          {:a [5], :b {:c 6, :d #{7}}, :f 8}))))

(deftest test-glob
  (let [files (task/glob {::task/root "./src/jmh"} "*.clj")]
    (is (= 2 (count (for [f files
                          :when (instance? java.io.File f)]
                      f))))))

(deftest test-merge-environment
  (is (= {:options {:foo {:mode :average, :fork 3}}}
         (task/merge-environment {:files [[:glob "j*.edn"]]})))
  (is (= {:options {:foo {:mode :single-shot, :threads 2, :fork 3}}}
         (task/merge-environment {:files ["jmh.edn" [:resource "jmh.edn"]]})))
  (is (= {:options {:foo {:mode :single-shot, :threads 2, :fork 3}}}
         (task/merge-environment {:files [[:glob "j*.edn"] [:glob "**/j*.edn"]]})))
  (is (= {:options {:foo {:mode :single-shot, :threads 2}}}
         (task/merge-environment {:files [[:glob "dev-resources/*.edn"]]}))))

(deftest test-align-column
  (let [rows [{:a [1 "ab"]}
              {:b 42}
              {:a [1000 "c"]}
              {:a [0 "defgh"]}]]
    (is (= [] (task/align-column :a 2 str str [])))
    (is (= [{:a "1     ab"}
            {:b 42}
            {:a "1000  c"}
            {:a "0     defgh"}]
           (task/align-column :a 2 str str rows)))))

(deftest test-format-table
  (testing "empty"
    (is (= "" (task/format-table [:a :b] {})))
    (is (= "" (task/format-table [:a :b] [{:c 42}]))))

  (testing "row width"
    (is (= [":a "
            "---"
            "100"]
           (lines (task/format-table [:a :b] [{:a 100}])))))

  (testing "col width"
    (is (= [":a  :foo"
            "--  ----"
            "    0   "
            "0       "]
           (lines (task/format-table [:a :foo] [{:foo 0} {:a 0}]))))))

(deftest test-pr-str-max
  (are [s x n] (= s (task/pr-str-max 'x n " ... "))
    " ... o"               pkg.Foo 6
    "my.ns/bar"            my.ns/bar 9
    "my ... ux"            my.ns/quux 9
    "my.ns/ ... ng-name"   my.ns/really-long-name 18
    "my.ns/r ... ng-name"  my.ns/really-long-name 19
    "[1 2 \" ... x\" 3 4]" [1 2 "foo bar quux" 3 4] 18))

(deftest test-prepare-result
  (let [result [{:a 1, :b 2, :c 3, :d 4}
                {:a 0, :b 3, :d 2}]]
    (is (= [{:a 1}, {:a 0}]
           (task/prepare-result result {:exclude #{}, :only [:a]})))
    (is (= [{:c 3, :d 4}, {:d 2}]
           (task/prepare-result result {:exclude #{:a :b}})))
    (is (= [{:a 1, :d 4}, {:a 0, :d 2}]
           (task/prepare-result result {:exclude #{:a}, :only [:a :d]})))))

(deftest test-progress-reporter
  (let [s (with-out-str
            (let [f (task/progress-reporter *out*)]
              (f {:percent 0.5, :eta 17})
              (f {:percent 0.02, :eta 42})
              (f {:percent 1, :eta 0})))]
    (is (= (str "{:% 50.0 :eta \"00:00:17\"}\r"
                "{:% 2.0 :eta \"00:00:42\"} \r"
                "{:% 100.0 :eta \"00:00:00\"}\n")
           (.replace s "\r\n" "\n")))))

(deftest test-report-table
  (let [result [{:fn 'foo
                 :percentiles
                 (sorted-map
                  50.0 {:samples 2, :score [17.0 "s/op"]}
                  0.0 {:samples 1, :score [42.0 "s/op"]}
                  100.0 {:samples 3, :score [100.0 "s/op"]})
                 :secondary
                 (sorted-map
                  "gc.time" {:samples 5, :score [100 "y/sec"]}
                  "gc.count" {:samples 4, :score [1 "x/sec"]})}
                {:fn '(bar)
                 :params {:p {}}}
                {:method 'pkg.Quux/run
                 :secondary
                 (sorted-map "some.metric" {:samples 6, :score [1e-6 "z/sec"]})}]]
    (is (= [":benchmark     :samples  :score          :params"
            "-------------  --------  --------------  -------"
            "foo                                             "
            "  0.0%         1         42.000   s/op          "
            "  50.0%        2         17.000   s/op          "
            "  100.0%       3         100.000  s/op          "
            "  gc.count     4         1.000    x/sec         "
            "  gc.time      5         100.000  y/sec         "
            "(bar)                                    {:p {}}"
            "pkg.Quux/run                                    "
            "  some.metric  6         ≈ 10⁻⁶   z/sec         "]
           (lines (with-out-str
                    (task/report :table result)))))))

(deftest test-result-comparator
  (let [xs [{:a 1, :b 2, :c 3}
            {:a 1, :c 2}
            {:a 0, :b 1}]
        sort #(sort (task/result-comparator %) xs)]

    (testing "none"
      (is (= xs (sort nil)))
      (is (= xs (sort [:x])))
      (is (= xs (sort [:x :desc]))))

    (testing "asc"
      (is (= [{:a 0, :b 1}
              {:a 1, :b 2, :c 3}
              {:a 1, :c 2}]
             (sort [:a])))
      (is (= [{:a 0, :b 1}
              {:a 1, :c 2}
              {:a 1, :b 2, :c 3}]
             (sort [:a [:c :asc]]))))

    (testing "desc"
      (is (= [{:a 1, :b 2, :c 3}
              {:a 1, :c 2}
              {:a 0, :b 1}]
             (sort [:c :desc])))
      (is (= [{:a 0, :b 1}
              {:a 1, :b 2, :c 3}
              {:a 1, :c 2}]
             (sort [:a [:c :desc]]))))))
