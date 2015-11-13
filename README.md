# pipes

A Clojure library that lets you chain processes and threads via pipes. It has been well tested in production.

If you ever used Un*x pipes

```
$ ls | grep .clj
```

then this library gives you a power to do this in Clojure and a lot more.

## Features

- Composable pipelines of shell processes, local threads and custom jobs.
- Cancel the entire pipeline.
- Terminates the entire pipeline if one of jobs fails.
- Block to wait for result or use callbacks.
- Success/error/cancel callbacks.
- Finalizer callbacks (similar to `finally` for `try`).

## Usage

Add `pipes` to `:dependencies` in project.clj:

[![Clojars Project](http://clojars.org/bilus/pipes/latest-version.svg?2394892384)](http://clojars.org/bilus/pipes)

Use `pipes.core` for core functions and macros and `pipes.shell` for the `exec` function.

The examples below assume:

```clojure
(require '[pipes.core :refer [->pipe pipe-> ->pipe-> ->job->]]
         '[pipes.shell :refer [exec]])
```

## Quick start

Pipe the contents of a readme through `grep` to find lines with word "clojure" then pipes
the lines through `wc` to count them. To make the example simple, the result is written
a file, but `out` can be any output stream.

```clojure
(with-open [in  (input-stream  "README.md")             ; (1)
            out (output-stream "line_count.txt")]        
    (->pipe->                                           ; (2)
      in
      [(exec ["grep" "clojure"])                        ; (3)
       (exec ["wc" "-l"])]                              
      out))                                               
```        

(1) We need to close the input and output streams. Any intermediate streams are managed by the library itself.

(2) We use `->pipe->` function which takes an input stream, pipes it through one or more jobs and writes the result to an output stream.

(3) In this example we use only shell but the next one show how you can pipe through regular Clojure functions as well.

This all happens without reading the entire file contents into memory which means
we can dump our 10GB Postgres database and encrypt it on the fly like this:


```clojure
(with-open [out (output-stream "mydb_backup.enc")]  ; (1)
    (pipe->                                         ; (2)
      [(exec ["pg_dump mydb"])                      ; (3)
       (->job-> [in out]                            ; (4)
         (encrypt in out "mypassword123"))]         ; (5)
      out))
```

(1) We need to close the output stream `out` because we own it. Intermediate streams are managed by the library itself.

(2) Because `pg_dump` doesn't need to read from standard input, we use `pipe->` instead of `->pipe->` we needed above. 

(3) The `exec` function in included in this library.

(4) The `->job->` macro creates a local thread. The example function `encrypt` above
reads from `InputStream` `in` and writes to `OutputStream` `out`.

Note that encryption happens on the fly; as `pg_dump` writes to its standard output it is piped to the `encrypt` function.

The example project TODO shows how to use the excellent nippy library to easily encrypt on the fly.

What can you do with the return value? 

```clojure
  (def j (pipe-> ...))
  (j)           ;; (1)
  (realized? j) ;; (2)
  @j            ;; (3)
```

It's a future object with a bit of extra functionality:

(1) You can cancel the entire pipeline.

(2) Check if it has completed.

(3) Block until it completes.

## TODO

- Readme
  - Pipe functions.
  - Shell exec
    - Custom exit codes.
  - Futures
  - Cancellation
  - Blocking
  - IO helpers.
  - Custom jobs via job-ctl
  - Composing jobs
  - Job trees.



## License

Copyright © 2015 Marcin Bilski, Designed.ly

Distributed under the Eclipse Public License either version 1.0 or any later version.
