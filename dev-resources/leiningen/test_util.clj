(ns leiningen.test-util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.project :as project]
            [leiningen.core.user :as user]
            [leiningen.clean :as clean])
  (:import [java.io ByteArrayOutputStream FileDescriptor FileOutputStream PrintStream]))

(defn project-file
  "Return a File object of the leiningen project file in `dir`."
  ^java.io.File [dir]
  (let [file (io/file dir "project.clj")]
    (assert (some-> file .isFile)
            (str "project file not found in directory: " dir))
    file))

(defn read-project
  "Read and initialize the project.clj file from the given directory."
  ([dir] (read-project {} dir))
  ([profiles dir]
   (with-redefs [user/profiles (constantly profiles)]
     (let [file (project-file dir)
           project (project/read (str file))]
       (project/init-project
        (project/project-with-profiles-meta
         project (merge @project/default-profiles (:profiles project))))))))

(defn run-task-in-project
  "Apply `task` fn in the given project directory `dir` with optional
  argument seq `args` and available `profiles`. Returns a map of the
  standard :out and :err from running the task.

  Each argument will be converted to a string (with `pr`) before passing
  to the task."
  ([dir task] (run-task-in-project dir task {} nil))
  ([dir task args] (run-task-in-project dir task args {}))
  ([dir task args profiles]
   (let [file (project-file dir)
         target (io/file (.getParent file) "target")
         targets ^{:protect false} [(str target)]
         profiles (update-in profiles [:user] assoc
                             :clean-targets targets)
         project (read-project profiles dir)
         args (for [a args]
                (binding [*print-dup* false]
                  (with-out-str (pr a))))

         out (ByteArrayOutputStream.)
         err (ByteArrayOutputStream.)
         streams (fn []
                   (let [ss [(.toString out "UTF-8")
                             (.toString err "UTF-8")]]
                     (zipmap [:out :err] (map str/trim ss))))]
     (try
       (System/setOut (PrintStream. out))
       (System/setErr (PrintStream. err))
       (apply task project args)
       (streams)
       (catch clojure.lang.ExceptionInfo e
         (as-> (:err (streams)) s
           (and (seq s) (println s)))
         (throw e))
       (finally
         (System/setOut (-> FileDescriptor/out FileOutputStream. PrintStream.))
         (System/setErr (-> FileDescriptor/err FileOutputStream. PrintStream.))
         (clean/clean project))))))
