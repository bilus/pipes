(ns pipes.job
  "Cancellable and composable asynchronous jobs similar to futures (but also cancelable)
   but more flexible in that they can be used for any asynchronous operations such as
   go co-routines, threads, futures or system processes."
  (:require [pipes.exceptions :refer [unwrap-exception]]
            [clojure.core.async :refer [chan <! >! >!! <!! go-loop dropping-buffer alts!]])
  (:import [clojure.lang IDeref
                         IFn
                         IPending]
           [java.util.concurrent CancellationException ExecutionException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(declare job-ctl)

(defn wrap-job
  "Wraps an asynchronous job so errors are reported via a callback and finalizer called in all cases.
   Sadly, it creates a future for each job but ensures that `error` callback is immediately called
   for the first job that fails leading to better error reporting. If we were to simply handle that
   in `wait-for-jobs` instead, which exception would be reported to the user would be unpredictable
   and remember that we may receive \"Pipe closed\" errors when used in `->pipes->`. We don't want
   to report these but the original exception in the job that really failed."
  [started-job & {:keys [error finalizer success cancel] :or {error identity finalizer #() cancel #() success #()}}]
  (let [wrapper (future
                  (try
                    @started-job
                    (success)
                    (catch CancellationException _
                      (cancel))
                    (catch InterruptedException _
                      (cancel))
                    (catch Exception e
                      (error (unwrap-exception ExecutionException e)))
                    (finally
                      (finalizer))))]
    (job-ctl
      {:invoke-fn      #(do
                         (started-job)
                         (future-cancel wrapper))
       :is-realized-fn #(realized? wrapper)
       :deref-fn       #(deref wrapper)})))

(defn cancel-all-jobs
  "Attempts to cancel each job in `jobs`"
  [jobs]
  (doseq [job jobs]
    (job)))

(defn wait-for-jobs
  "Wait for jobs to signal their completion through `result-ch`. It also handles
   errors and cancellation signalled through the channel."
  [jobs result-ch cancel-ch]
  (go-loop [jobs-left (count jobs)]
    (if (> jobs-left 0)
      (when-let [[r _ch] (alts! [cancel-ch result-ch])]
        (if (= :done (:status r))
          (recur (dec jobs-left))
          r))
      {:status :success})))

(defn emit-cancel
  [cancel-ch]
  (>!! cancel-ch {:status :cancel}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn job-ctl
  "Creates a custom job controller supporting cancellation, blocking wait and checking state
    using `realized?`. "
  [{:keys [invoke-fn is-realized-fn deref-fn]}]
  (letfn [(call-if-present [f]
                           (when f
                             (f)))]
    (reify
      IFn
      (invoke [_]
        (call-if-present invoke-fn))
      IPending
      (isRealized ^boolean [_]
        (call-if-present is-realized-fn))
      IDeref
      (deref [_]
        (call-if-present deref-fn)))))

(defn future->job
  "Converts `future` to an asynchronous job."
  [future]
  (job-ctl
    {:invoke-fn      #(future-cancel future)
     :is-realized-fn #(realized? future)
     :deref-fn       #(deref future)}))

(defmacro job
  "Returns a job running `body`. The job can be derefed to block until it finishes or canceled.
   You can also call `realized?` to check if it has completed.

   <code><pre>(let [long-job (job (Thread/sleep 100000))]
                (println (realized? long-job))       ; => false
                (long-job)                           ; Cancel the job.
                @long-job)                           ; Wait for it to finish.
                ; Here a CancellationException will be thrown when job is deref'ed.
   </code></pre>"
  [& body]
  `(future->job
     (future
       (do ~@body))))

(defn compose-jobs
  "Compose one or more started jobs to let them be cancelled and waited on together.
   In addition, an exception thrown by any of the jobs will cancell all other jobs."
  [jobs & {:keys [cancel success error finalizers] finally-fn :finally
           :or   {cancel     (fn default-cancel [])
                  success    (fn default-success [])
                  error      (fn default-error [_])
                  finally-fn (fn default-finally [])
                  finalizers (repeat (fn default-finalizer []))}}]
  (let [result-ch (chan)
        cancel-ch (chan (dropping-buffer 1))                ; Ignore subsequent cancels.
        started-jobs (doall
                       (map
                         (fn [job finalizer] (wrap-job job
                                                       :success (fn success-fn []
                                                                  (>!! result-ch {:status :done}))
                                                       :finalizer (fn finalizer-fn []
                                                                    (finalizer))
                                                       :error (fn error-fn [e]
                                                                (>!! result-ch {:status    :error
                                                                                :exception e}))
                                                       :cancel (fn cancel-fn []
                                                                 (emit-cancel cancel-ch))))
                         jobs finalizers))
        monitor (future
                  (let [result (<!! (wait-for-jobs started-jobs result-ch cancel-ch))]
                    (try (case (:status result)
                           :cancel (cancel)
                           :error (do (cancel-all-jobs started-jobs)
                                      (error (:exception result)))
                           (success))
                         (finally
                           (finally-fn)
                           (when-let [e (:exception result)]
                             (throw e))))))]
    (job-ctl
      {:invoke-fn      #(do (emit-cancel cancel-ch)
                            (cancel-all-jobs started-jobs))
       :is-realized-fn #(and (every? realized? started-jobs) (realized? monitor))
       :deref-fn       #(do (try
                              @monitor
                              (catch ExecutionException e
                                (throw (unwrap-exception ExecutionException e)))))})))
