(ns pipes.core-test
  (:require
    [pipes.core :refer :all]
    [pipes.job :refer :all]
    [pipes.test-helpers :refer :all]
    [clojure.core.async :refer [alts!! timeout chan >!! go-loop go >! <! close!]]
    [clojure.test :refer [are is deftest with-test run-tests testing]]
    [clojure.java.io :as io]
    [pipes.exceptions :as ex])
  (:import
    [java.lang RuntimeException]
    [java.io ByteArrayInputStream
             ByteArrayOutputStream FileNotFoundException]))

(deftest non-blocking-pipeline-test
  (testing "success path"
    (testing "pipes input to output"
      (let [in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (io/copy in out)))
            _pipe @(->pipe-> in
                             [identity-job
                              identity-job]
                             out)]
        (is (= (.toString out) "foo bar"))))
    (testing "invokes success callback and finally callback"
      (let [result-ch (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (io/copy in out)))
            _pipe @(->pipe-> in
                             [identity-job
                              identity-job]
                             out
                             :success #(>!! result-ch :success)
                             :finally #(>!! result-ch :finally)
                             :cancel #(>!! result-ch :cancel))]
        (is (matching? [:success :finally] (take-all! result-ch)))))
    (testing "gives each job a chance to finish"
      (let [in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (Thread/sleep 100)
                             (io/copy in out)))
            _pipe @(->pipe-> in
                             [identity-job
                              identity-job]
                             out)]
        (is (= (.toString out) "foo bar")))))
  (testing "deref"
    (testing "waits for all to complete"
      (let [in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (Thread/sleep 50)
                             (io/copy in out)))
            _pipe @(->pipe-> in
                             [identity-job
                              identity-job]
                             out)
            result (.toString out)]
        (is (= result "foo bar")))))
  (testing "cancel"
    (testing "cancels each job"
      (let [cancellations (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            slow-job (fn [job-id]
                       (fn [_in _out]
                         (let [slow-future (future (Thread/sleep 10000))]
                           (job-ctl
                             {:invoke-fn      #(do (>!! cancellations job-id)
                                                   (future-cancel slow-future))
                              :is-realized-fn #(realized? slow-future)
                              :deref-fn       #(deref slow-future)}))))
            pipe (->pipe-> in
                           [(slow-job :job1)
                            (slow-job :job2)]
                           out)]
        (pipe)
        (is (matching? [:job1 :job2] (take-all! cancellations)))))
    (testing "invokes cancel callback and finally callback"
      (let [result-ch (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            slow-job (fn [job-id]
                       (fn [_in _out]
                         (let [slow-future (future (Thread/sleep 10000))]
                           (job-ctl
                             {:invoke-fn      #(future-cancel slow-future)
                              :is-realized-fn #(realized? slow-future)
                              :deref-fn       #(deref slow-future)}))))
            pipe (->pipe-> in
                           [(slow-job :job1)
                            (slow-job :job2)]
                           out
                           :success #(>!! result-ch :success)
                           :finally #(>!! result-ch :finally)
                           :cancel #(>!! result-ch :cancel))]
        (pipe)
        (ex/ignore-any-exception
          @pipe)
        (is (matching? [:cancel :finally] (take-all! result-ch))))))
  (testing "exceptions"
    (testing "invokes error callback and finally callback"
      (let [result-ch (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (io/copy in out)))
            failing-job (fn [in out]
                          (job
                            (throw (RuntimeException. "An error"))))
            pipe (->pipe-> in
                           [identity-job
                            failing-job]
                           out
                           :success #(>!! result-ch :success)
                           :finally #(>!! result-ch :finally)
                           :cancel #(>!! result-ch :cancel)
                           :error (fn [_] (>!! result-ch :error)))]
        (ex/ignore-any-exception
          @pipe)
        (is (matching? [:error :finally] (take-all! result-ch)))))
    (testing "invokes error callback with first exception thrown"
      (let [result-ch (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            failing-job (fn [throw-after msg]
                          (fn [in out]
                            (job
                              (Thread/sleep throw-after)
                              (throw (RuntimeException. msg)))))
            pipe (->pipe-> in
                           [(failing-job 1000 "Error from first job")
                            (failing-job 1 "Error from second job")]
                           out
                           :error (fn [e] (>!! result-ch (.getMessage e))))]
        (try @pipe (catch RuntimeException _))
        (is (matching? ["Error from second job"] (take-all! result-ch)))))
    (testing "throws first exception on deref"
      (let [in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            failing-job (fn [throw-after msg]
                          (fn [in out]
                            (job
                              (Thread/sleep throw-after)
                              (throw (RuntimeException. msg)))))
            pipe (->pipe-> in
                           [(failing-job 1000 "Error from first job")
                            (failing-job 1 "Error from second job")]
                           out)]
        (is (thrown-with-msg? RuntimeException #"Error from second job" @pipe))))
    (testing "cancels blocked jobs"
      (let [cancellations (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            slow-job (fn [job-id]
                       (fn [_in _out]
                         (let [slow-future (future (Thread/sleep 1000))]
                           (job-ctl
                             {:invoke-fn      #(do (>!! cancellations job-id)
                                                   (future-cancel slow-future))
                              :is-realized-fn #(realized? slow-future)
                              :deref-fn       #(deref slow-future)}))))
            failing-job (fn [in out]
                          (job
                            (throw (RuntimeException. "An error"))))
            pipe (->pipe-> in
                           [(slow-job :job1)
                            (slow-job :job2)
                            failing-job]
                           out)]               ; Make test complete faster.
        (try @pipe (catch RuntimeException _))
        (is (matching? [:job1 :job2] (take-all! cancellations)))))
    (testing "no output stream"
      (let [in (ByteArrayInputStream. (.getBytes "foo bar"))
            aux-out (ByteArrayOutputStream.)
            identity-job (fn [in out]
                           (job
                             (io/copy in out)))
            saving-job (fn [in _out]
                         (job
                           (io/copy in aux-out)))
            _pipe @(->pipe in
                           [identity-job
                            saving-job])]
        (is (= (.toString aux-out) "foo bar"))))
    (testing "no input stream"
      (let [out (ByteArrayOutputStream.)
            producer-job (fn [_ out]
                           (job
                             (.write out (.getBytes "foo bar"))))
            identity-job (fn [in out]
                           (job
                             (io/copy in out)))
            _pipe @(pipe-> [producer-job
                            identity-job]
                           out)]
        (is (= (.toString out) "foo bar"))))
    (testing "support input file streams but no string paths"
      (with-open [in (io/input-stream "README.md")
                  out (ByteArrayOutputStream.)]
        (let [identity-job (fn [in out]
                             (job
                               (io/copy in out)))
              _pipe @(->pipe-> in
                               [identity-job]
                               out)]
          (is (= (.toString out) (slurp "README.md"))))))
    (testing "support output file streams but no string paths"
      (try
        (with-open [in (io/input-stream "README.md")
                    out (io/output-stream "README.md~")]
          (let [identity-job (fn [in out]
                               (job
                                 (io/copy in out)))
                _pipe @(->pipe-> in
                                 [identity-job]
                                 out)]))
        (is (= (slurp "README.md") (slurp "README.md~")))
        (finally
          (.delete (io/file "README.md~")))))
    (testing "realized?"
      (let [cancellations (chan 1024)
            in (ByteArrayInputStream. (.getBytes "foo bar"))
            out (ByteArrayOutputStream.)
            slow-job (fn [job-id]
                       (fn [_in _out]
                         (let [slow-future (future (Thread/sleep 10000))]
                           (job-ctl
                             {:invoke-fn      #(do (>!! cancellations job-id)
                                                   (future-cancel slow-future))
                              :is-realized-fn #(realized? slow-future)
                              :deref-fn       #(deref slow-future)}))))
            pipe (->pipe-> in
                           [(slow-job :job1)
                            (slow-job :job2)]
                           out)]
        (is (not (realized? pipe)))
        (pipe)
        @pipe
        (is (realized? pipe))))))
