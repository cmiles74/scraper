(defproject com.nervestaple/scraper "0.1.0-SNAPSHOT"
  :description "Simple WebEngine Based Scraper"
  :url "http://github.com/cmiles74/scraper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [com.taoensso/timbre "4.10.0"]
                 [cheshire "5.8.0"]]
  :profiles {:dev {:source-paths ["dev"]}}
  :repl-options {:init-ns user})
