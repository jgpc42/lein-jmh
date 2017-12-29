(ns jmh.task-test
  (:require [jmh.task :as task]
            [clojure.test :refer :all]))

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
    (is (= (str ":a \n"
                "---\n"
                "100")
           (task/format-table [:a :b] [{:a 100}]))))

  (testing "col width"
    (is (= (str ":a  :foo\n"
                "--  ----\n"
                "    0   \n"
                "0       ")
           (task/format-table [:a :foo] [{:foo 0} {:a 0}])))))

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
           s))))

(deftest test-report-table
  (let [result '[{:fn foo
                  :percentiles
                  {0.0 {:samples 1, :score [42.0 "s/op"]}
                   0.5 {:samples 2, :score [17.0 "s/op"]}}}
                 {:fn (bar)
                  :params {:p {}}}
                 {:method pkg.Quux/run
                  :profilers
                  {"some.metric" {:samples 3, :score [1e-6 "x/sec"]}}}]]

    (is (= (str ":benchmark     :samples  :score         :params\n"
                "-------------  --------  -------------  -------\n"
                "foo                                            \n"
                "  0.0%         1         42.000  s/op          \n"
                "  0.5%         2         17.000  s/op          \n"
                "(bar)                                   {:p {}}\n"
                "pkg.Quux/run                                   \n"
                "  some.metric  3         ≈ 10⁻⁶  x/sec         \n")
           (with-out-str
             (task/report :table result))))))

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
