(ns user
  (:use [clojure.repl])
  (:require [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [com.nervestaple.scraper.core :as core]
            [com.nervestaple.scraper.artoo :as artoo]
            [com.nervestaple.scraper.sync :as scraper]
            [com.nervestaple.scraper.demo :as demo]))

(def LOG-FILE "scraper.log")

(defn start-file-logging
  "Routes all log messages to the provided file."
  [filename]
  (timbre/merge-config!
   {:appenders {:spit (appenders/spit-appender {:fname filename})}}))

(defn stop-file-logging
  "Stops the file logging mechanism."
  []
  (timbre/merge-config! {:appenders {:spit nil}}))

(defn setup []
  (start-file-logging LOG-FILE))

(defonce setup-init (setup))

(defn pretty-map
  ([map] (pretty-map map 0))
  ([map level]
     (apply str (for [key (keys map)]
                  (if (map? (map key))
                    (str (apply str (repeat (inc level) " ")) key " -> {\n"
                         (pretty-map (map key) (inc level))
                         (apply str (repeat (inc level) " ")) "}\n")
                    (str (apply str (repeat (inc level) " ")) key " -> " (map key) "\n"))))))
