(defproject bilus/pipes "0.1.2"
  :description "A Clojure library that lets you chain processes and threads via pipes."
  :url "https://github.com/bilus/pipes"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/core.async "0.2.374"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]]}})
