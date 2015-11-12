# pipes

A Clojure library that lets you chain processes and threads via pipes.

If you ever used Un*x pipes

$ ls | grep .clj

then this library gives you a power to do this in Clojure and a lot more.

## Features

- Composable pipelines of shell processes, local threads and custom jobs.
- Cancel the entire pipeline.
- Terminate the entire pipeline if one of jobs fails.
- Block to wait for result or use callbacks.
- Success/error/cancel callbacks.
- Finalizer callbacks (similar to `finally` for `try`).

## Quick example

Just shell commands (writing output to a file for convenience):

```clojure
(pipe-> [(exec ["ls"])
         (exec ["grep" ".clj"])]
        (output-stream "output.txt"))
```        

But this is trivial, here's a shell command piped to a local thread (uses futures internally).

```clojure
(pipe-> [(shell ["pg_dump mydb"])
         (fn [in out]
           (job (encrypt in out "mypassword123")))]
        (output-stream "mydb_backup.enc"))
```

The `shell` function above comes with the library while the `encrypt` is just an example function that
reads from input stream `in` and writes to output stream `out`.

The example project TODO shows how to use the excellent nippy library to easily create such a function.

What can you do with the return value?

```clojure
  (def j (pipe-> ...))
  (j)           ;; 1.
  (realized? j) ;; 2.
  @j            ;; 3.
```

1. You can cancel the entire pipeline.
2. Check if it has completed.
3. Block until it completes.

## TODO

+ NullOutputStream

- Readme
  - Rationale
  - Quick example: dump postgres > encrypt and compress > upload
  - Features
    - Piping.
    - Cancellation.
    - Error handling.
    - Blocking.
    - isRealized.
    - Finalizer callbacks.
    - Success/error/cancel callbacks.
    - Finally callbacks.
    - Support for processes, futures and custom jobs.
  - Pipe functions.
  - Shell
  - Futures
  - Cancellation
  - Promises
  - Custom jobs via job-ctl
  - Composing jobs
  - Job trees.
  
- from-string
- to-string (?)

## License

Copyright © 2015 Designed.ly, Marcin Bilski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
