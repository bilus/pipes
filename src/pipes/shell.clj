(ns pipes.shell
  (:require [pipes.timeout :refer [with-timeout]]
            [pipes.job :as j]
            [pipes.exceptions :refer [ignore-exception]]
            [clj-commons-exec :as exec]
            [clojure.string :as str])
  (:import [org.apache.commons.exec ExecuteWatchdog]
           [java.util.concurrent TimeoutException]
           [java.util Calendar]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation

(def destroy-process-timeout 500)                           ; Cannot be a dynamic var because it's read in a separate thread below.

(defn default-error-handler
  [args {exception :exception :as result}]
  {:pre [(some? exception)]}
  (if-let [out (or (:err result) (:out result))]
    (let [lines (as-> out $
                  (clojure.string/split $ #"\n")
                  (take 50 $))]
      ;; (log/warn exception
      ;;           "node.system/run-process failed:"
      ;;           "\n>"
      ;;           (str/join " " args)
      ;;           "\n"
      ;;           (clojure.string/join "\n " lines))
      (throw (ex-info (.getMessage exception)
                      {:cause exception
                       :out lines
                       :args args})))
    (throw exception)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn exec
  "Runs a process with `args` being a vector.

   Options:
    :in               is fed to the sub-process's stdin
    :close-in?        true to close the input stream
    :out              used as stdout of the sub-process
    :close-out?       true to close the output stream
    :handle-quoting?  false to turn off quote handling (defaults to true)
    :on-error         fn to handle error conditions (fn [args {:keys [exception out err])
                      overriding the default which is logging an error and throwing exception
                      (see `default-error-handler`).

   Example: `(run-process [\"ls\" \"-al\" \"*\"] :handle-quoting? false)`

   Returns a job (see `node.job`). The result of derefing the job is a hashmap along the lines of:
   `{:exit 0 :out \"This is output\" :err nil}`"
  [args & [{on-error :on-error :or {on-error default-error-handler} :as opts}]]
  (fn [in out]
    (let [aborted (atom false)
          watchdog (ExecuteWatchdog. ExecuteWatchdog/INFINITE_TIMEOUT)
          promise (exec/sh args (merge {:shutdown true
                                        :watchdog watchdog
                                        :in in
                                        :close-in? false
                                        :out out
                                        :close-out? false} opts))]
      (j/job-ctl
       {:invoke-fn      #(do (reset! aborted true)
                             (ignore-exception TimeoutException
                                               (with-timeout destroy-process-timeout ;; .destroyProcess hangs when process is no longer running
                                                 (.destroyProcess watchdog))))
        :is-realized-fn #(realized? promise)
        :deref-fn       #(let [result (merge @promise {:aborted @aborted})]
                           (if-let [ex (:exception result)]
                             (on-error args result)
                             result))}))))
