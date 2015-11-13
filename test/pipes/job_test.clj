(ns pipes.job-test
  (use clojure.test
       pipes.job
       pipes.test-helpers)
  (:require [clojure.core.async :refer [chan >!!]]
            [pipes.exceptions :as ex]))

(deftest compose-jobs-test
  (testing "fix nested composed jobs hanging on exception"
    (let [j (compose-jobs
              [(compose-jobs
                 [(job (throw (RuntimeException. "THIS IS A TEST")))])])]
      (Thread/sleep 100)
      (is (realized? j))))

  (testing "wait for more than one job"
    (let [j (compose-jobs
              [(compose-jobs
                 [(job) (job)])])]
      (Thread/sleep 100)
      (is (realized? j))))
  (testing "invokes cancel callback and finally callback"
    (let [result-ch (chan 1024)
          pipe (compose-jobs
                 [(job (Thread/sleep 10000))]
                         :success #(>!! result-ch :success)
                         :finally #(>!! result-ch :finally)
                         :cancel #(>!! result-ch :cancel))]
      (pipe)
      (ex/ignore-any-exception
        @pipe)
      (is (matching? [:cancel :finally] (take-all! result-ch))))))
