(ns pipes.exceptions
  "Exception-handling utility functions.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn unwrap-exception
  "Unrap nested exception(s) of type `ex-type` to get to the cause."
  ([ex-type e]
   (unwrap-exception ex-type e 10))                         ;; Limit how deep we go through exception causes.
  ([ex-type e depth]
   (if (and (= ex-type (type e)) (> depth 0))
     (unwrap-exception ex-type (.getCause e) (dec depth))
     e)))

(defmacro ignore-exception
  "Returns the result of evaluating body, or nil if it throws exception of type e."
  [e & body]
  `(try
     ~@body
     (catch ~e e#
       #_(println "Ignoring" e#))))

(defmacro ignore-any-exception
  "Returns the result of evaluating body, or nil if it throws an exception."
  [& body]
  `(try ~@body (catch Exception _# nil)))
