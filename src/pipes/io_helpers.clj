(ns pipes.io-helpers
  (:import [java.io
            ByteArrayInputStream ByteArrayOutputStream
            OutputStream]))

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

(defn string->input-stream
  "Creates an input stream containing the bytes of string `str`."
  [^String str]
  {:pre [(string? str)]}
  (ByteArrayInputStream. (.getBytes str)))

(defn bytes->input-stream
  "Creates an input stream containing the bytes."
  [^bytes b]
  (ByteArrayInputStream. b))

(defn byte-array-output-stream
  "Creates a byte array output stream.
   Use .toString or str to get the resulting string or .toByteArray for the resulting byte array."
  []
  (ByteArrayOutputStream.))
