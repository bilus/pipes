(ns pipes.core
  "Cancellable asynchronous jobs linked by pipes."
  (:require [clojure.java.io :as io]
            [pipes.job :as j]
            [pipes.io-helpers :as ioh])
  (:import [java.io PipedOutputStream PipedInputStream
            Closeable InputStream OutputStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(defn make-connected-pipes
  []
  (let [out (PipedOutputStream.)
        in (PipedInputStream. out)]
    [out in]))

(defn closeable?
  [x]
  (instance? Closeable x))

(defn start-job
  [job-fn in out & {:keys [error] :or {error identity}}]
  (try
    (job-fn in out)
    (catch Exception e
      (j/job
        (throw e)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ->pipe->
  "Pipe data from `in` through `jobs` to `out`.
   In addition, the user can define one or more callbacks: :cancel :success :error :finally.
   Returns a job (see `pipes.job`)"
  [^InputStream in jobs ^OutputStream out & {:keys [cancel success error finally]
                                             :or   {cancel #() success #() error identity finally #()}}]
  (let [pipes (->> (repeatedly make-connected-pipes)
                   (take (dec (count jobs)))
                   flatten)
        started-jobs (->> (concat (list in) pipes (list out))
                          (partition 2)
                          (mapv (fn start-fn [job-fn [in out]]
                                  (start-job job-fn in out))
                                jobs)
                          doall)
        finalizers (->> (concat (list nil) pipes (list nil)) ; Do not close `in` and `out`; `->pipe->` doesn't own them.
                        (partition 2)
                        (map (fn make-finalizer-fn [[in out]]
                               (fn finalizer-fn []
                                 (when (closeable? in)
                                   (.close in))
                                 (when (closeable? out)
                                   (.close out))))))]
    (j/compose-jobs started-jobs
                    :finalizers finalizers
                    :cancel cancel
                    :success success
                    :error error
                    :finally (fn []
                               (finally)))))

(defn pipe->
  "Pipe data from `jobs` to `out` with the first job being the source of data.
   In addition, the user can define one or more callbacks: :cancel :success :error :finally.
   Returns a job (see `pipes.job`)"
  [jobs ^OutputStream out & opts]
  (apply ->pipe-> (ioh/null-input-stream) jobs out opts))

(defn ->pipe
  "Pipe data from `in` all the way through `jobs` with the last job being the consumer of the data.
   In addition, the user can define one or more callbacks: :cancel :success :error :finally.
   Returns a job (see `pipes.job`)"
  [^InputStream in jobs & opts]
  (apply ->pipe-> in jobs (ioh/null-output-stream) opts))

(defmacro ->job->
  "Create a new thread that reads from input stream, does some processing and writes to the output stream."
  [[^InputStream in ^OutputStream out] & body]
  `(fn [~in ~out]
     (j/job
      ~@body)))

