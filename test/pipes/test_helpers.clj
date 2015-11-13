(ns pipes.test-helpers
  (:require [clojure.core.async :refer [alts!! timeout chan >!! go-loop go >! <! close!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(def safe-take-timeout 100)

(defn safe-take!
  "Try to read from channel or return nil on timeout"
  [ch]
  (let [[v _] (alts!! [ch (timeout safe-take-timeout)])]
    v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn take!
  "Try to read from channel and throw on timeout."
  [ch]
  (or (safe-take! ch) (throw (ex-info "Timeout while trying to read from the port" {:type :timeout-exception}))))

(defmacro timeout?
  "Evals `body` and returns true if a timeout exception was thrown."
  [body]
  `(try
     (do
       ~body
       false)
     (catch clojure.lang.ExceptionInfo e#
       (= :timeout-exception (-> e# ex-data :type)))))

(defn take-all!
  "Read from ch until timeout and return a seq."
  [ch]
  (doall (take-while some? (repeatedly #(safe-take! ch)))))

(defn matching? [expected actual]
  "True if the sequences contain equal elements regardless of their ordering."
  (= (sort expected) (sort actual)))
