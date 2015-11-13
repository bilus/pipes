# pipes

A Clojure library that lets you chain processes and threads via pipes.

If you ever used Un*x pipes

```
$ ls | grep .clj
```

then this library gives you a power to do this in Clojure and a lot more.

## Features

- Composable pipelines of shell processes, local threads and custom jobs.
- Cancel the entire pipeline.
- Terminate the entire pipeline if one of jobs fails.
- Block to wait for result or use callbacks.
- Success/error/cancel callbacks.
- Finalizer callbacks (similar to `finally` for `try`).

## Quick example

Pipe the contents of a readme through `grep` to find lines with word "clojure" then pipes
the lines through `wc` to count them. To make the example simple, the result is written
a file, but `out` can be any output stream.

```clojure
(with-open [in  (input-stream  "README.md")
            out (output-stream "line_count.txt")]
    (->pipe-> [(exec ["grep" "clojure"])
               (exec ["wc" "-l"])]
            out))
```        

This all happens without reading the entire file contents into memory of course which means
we can dump our 10GB Postgres database and encrypt it on the fly like this:


```clojure
(with-open [out (output-stream "mydb_backup.enc")]
    (pipe-> [(exec ["pg_dump mydb"])
             (->job-> [in out]
                (encrypt in out "mypassword123"))]
            out))
```

Notice that because `pg_dump` doesn't need to read from standard input, we use `pipe->`
instead of `->pipe->` we needed above. There's also a `->pipe` variant.

In any case, we need to close the input and output streams because we own them. Intermediate
streams are managed by the library itself.

The `shell` function above comes with the library while the `encrypt` is just an example function that
reads from input stream `in` and writes to output stream `out`.

Note that encryption happens on the fly; as `pg_dump` writes to its standard output it is piped to the `encrypt` function.

The example project TODO shows how to use the excellent nippy library to easily encrypt on the fly.


What can you do with the return value? 

```clojure
  (def j (pipe-> ...))
  (j)           ;; 1.
  (realized? j) ;; 2.
  @j            ;; 3.
```

It's a future object with a bit of extra functionality:

1. You can cancel the entire pipeline.
2. Check if it has completed.
3. Block until it completes.

## TODO

+ NullOutputStream

- Readme
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
