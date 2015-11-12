(ns pipes.core
  "Cancellable asynchronous jobs linked by pipes."
  (:require [clojure.java.io :as io]
            [pipes.job :as j])
  (:import [java.io PipedOutputStream PipedInputStream
            ByteArrayInputStream
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

(defn null-input-stream
  "Creates an input stream containing no data."
  []
  (ByteArrayInputStream. (byte-array 0)))

(defn null-output-stream
  "Creates an output stream that discards anything written to it."
  []
  (proxy
    [OutputStream] []
    (close [])
    (flush [])
    (write ([_]) ([_ _ _]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ->pipe->
  "Pipe data from `in` through `jobs` to `out`.
   In addition, the user can define one or more callbacks: :cancel :success :error :finally.
   Returns a job (see `node.job`)"
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
   Returns a job (see `node.job`)"
  [jobs ^OutputStream out & opts]
  (apply ->pipe-> (null-input-stream) jobs out opts))

(defn ->pipe
  "Pipe data from `in` all the way through `jobs` with the last job being the consumer of the data.
   In addition, the user can define one or more callbacks: :cancel :success :error :finally.
   Returns a job (see `node.job`)"
  [^InputStream in jobs & opts]
  (apply ->pipe-> in jobs (null-output-stream) opts))

;; TODO: Macros to make the interface compatible with `node.pipeline`.