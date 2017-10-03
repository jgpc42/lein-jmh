(ns ^:internal leiningen.jmh.form
  "Code snippets for evaluation by the jmh task subprocess."
  (:require [leiningen.core.main :as main]))

(def ^{:private true
       :doc "The namespaces used by the forms in this file."}
  requires '[jmh.core clojure.pprint])

(def ^{:doc "The initialization code to be evaluated first."}
  init (list* `require (map (partial list 'quote)
                            requires)))

(def ^{:private true
       :doc "Table layout settings."}
  table-info
  {:benchmark-column :fn/method
   :data-keys [:params]
   :default-keys [:name :mode :samples :score
                  :score-error :threads :params]
   :expand-keys [:percentiles :profilers]
   :max-seq-length 48})

(defn- keyword-seq
  "Coerce value for a sequence of keywords."
  [x]
  (if (keyword? x) [x] x))

;;;

(def ^{:doc "A fn that will expand nested result maps and format results
            as rows based on `table-info`. Returns a seq of row maps."}
  table-rows
  `(fn [m#]
     (let [bench# (:fn m# (:method m#))
           bench# (if (seq? bench#)
                    (let [bench# (pr-str bench#)
                          len# (count bench#)
                          max# ~(:max-seq-length table-info)]
                      (if (> len# max#)
                        (str (subs bench# 0 (- max# 4)) " ...")
                        bench#))
                    bench#)

           normalize#
           (fn [m# bench#]
             (-> (assoc m# ~(:benchmark-column table-info) bench#)
                 ~@(for [k (:data-keys table-info)]
                     `(update ~k #(and % (pr-str %))))))

           expand#
           (fn [sub#]
             (for [[k# v#] sub#]
               (let [bench# (str "  " k#
                                 (when (number? k#) "%"))]
                 (normalize# v# bench#))))

           nested# (->> (select-keys m# ~(:expand-keys table-info))
                        vals
                        (mapcat expand#)
                        (sort-by ~(:benchmark-column table-info)))

           m# (apply dissoc (normalize# m# bench#)
                     :fn :method ~(:expand-keys table-info))]
       (cons m# nested#))))

(def ^{:doc "A fn that will format each result row's :score aligned for
            tabular display."}
  align-scores
  `(fn [rows#]
     (let [vals# (map (comp count str first :score) rows#)
           max# (apply max vals#)]
       (for [m# rows#
             :let [[n# unit#] (:score m#)
                   left# (str n#)
                   nspace# (+ 2 (- max# (count left#)))
                   sep# (apply str (repeat nspace# \space))]]
         (assoc m# :score (str left# sep# unit#))))))

(def ^{:doc "A fn that will report a progress event via the Writer
            object given as its first argument."}
  progress-reporter
  `(let [prev# (volatile! nil)]
     (fn [out# {eta# :eta, pct# :percent}]
       (let [line# (format "{:%% %.1f :eta \"%02d:%02d:%02d\"}"
                           (* 100.0 pct#)
                           (long (/ eta# (* 60.0 60)))
                           (long (/ (mod eta# (* 60.0 60)) 60))
                           (mod eta# 60))
             nline# (count line#)
             nprev# (count @prev#)
             prev?# (pos? nprev#)
             clear# (if (and prev?# (< nline# nprev#))
                      (apply str (repeat (- nprev# nline#) \space))
                      "")]
         (binding [*out* out#]
           (locking out#
             (when prev?#
               (print \return))
             (print line#)
             (print clear#)
             (when (>= pct# 1.0)
               (newline))
             (flush)
             (vreset! prev# line#)))))))

(defn finalize-options
  "Assemble the benchmark options map."
  [opts]
  `(let [opts# '~opts
         pval# (:progress opts# :out)]
     (cond
       (or (#{:out :err} pval#)
           (true? pval#))
       (assoc opts# :progress
              (partial ~progress-reporter
                       (if (= pval# :err) *err* *out*)))
       (not pval#)
       opts#
       :else
       (update opts# :progress eval))))

(defn print-table
  "Print the given column key values of the rows in a tabular format."
  [cols rows]
  `(let [rows# ~rows
         cols# (filter #(some % rows#) '~cols)

         widths# (for [k# cols#
                       :let [lens# (map (comp count str k#)
                                        rows#)]]
                   (apply max (cons (count (str k#)) lens#)))

         fmts# (map #(format "~%da" %) widths#)
         fmt# (str "~:{~%"
                   (apply str (interpose "  " fmts#))
                   "~}")

         seps# (for [w# widths#]
                 (apply str (repeat w# \-)))
         header# [cols# seps#]]

     (when (seq cols#)
       (->> rows#
            (map (apply juxt cols#))
            (map #(replace {nil ""} %))
            (concat header#)
            (clojure.pprint/cl-format true fmt#)))))

(defn result-comparator
  "A comparator fn that sorts results by their keys."
  [x]
  (let [xs (keyword-seq x)
        ks (if (#{:asc :desc} (second xs)), [xs] xs)

        [x y n] (map gensym ["x" "y" "n"])
        cmps (for [k ks
                   :let [[k order] (if (keyword? k)
                                     [k :asc]
                                     k)]]
               (if (= order :desc)
                 `(compare (~k ~y) (~k ~x))
                 `(compare (~k ~x) (~k ~y))))]
    `(fn [~x ~y]
       ~((fn go [[form & more]]
           (if more
             `(let [~n ~form]
                (if (zero? ~n)
                  ~(go more)
                  ~n))
             form))
         cmps))))

(defn run-benchmarks
  "Run the given benchmark environment and options."
  [env {out :output :as opts}]
  (let [only (seq (keyword-seq (:only opts)))
        exclude (set (keyword-seq (:exclude opts)))

        result (gensym "result")
        write (condp = (:format opts)
                nil
                `(prn ~result)
                :pprint
                `(clojure.pprint/pprint ~result)
                :table
                (let [cols (->> (or only (:default-keys table-info))
                                (remove exclude)
                                (cons (:benchmark-column table-info)))]
                  (print-table cols `(~(if (some #{:score} cols)
                                         align-scores `identity)
                                      (mapcat ~table-rows ~result))))
                #_:else
                (main/abort (str "Unknown :format: " (pr-str (:format opts)))))]

    `(try
       (let [~result (jmh.core/run '~env ~(finalize-options opts))

             ~result ~(if-let [ks (seq (:sort opts))]
                        `(sort ~(result-comparator ks) ~result)
                        result)

             ~result ~(if only
                        `(for [m# ~result]
                           (select-keys m# '~(remove exclude only)))
                        `(for [m# ~result]
                           (select-keys m# (remove '~exclude (keys m#)))))]

         ~(if (string? out)
            `(spit ~out (with-out-str ~write))
            `(binding [*out* ~(if (= :err out) `*err* `*out*)]
               ~write)))
       (catch Exception e#
         (.printStackTrace e#)
         (System/exit 1)))))
