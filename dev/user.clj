(ns user
  (:use [clojure.repl])
  (:require [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [com.nervestaple.scraper.sync :as scraper]
            [com.nervestaple.scraper.artoo :as r2]
            [com.nervestaple.scraper.gui :as gui]))

(def LOG-FILE "scraper.log")

(defn start-file-logging
  "Routes all log messages to the provided file."
  [filename]
  (let [stdoutfn (get-in @timbre/config [:appenders :standard-out :fn])
        fappender {:doc "Prints to file" :min-level :debug :enabled? true
                   :async? false
                   :ns-whitelist ["com.nervestaple"]
                   :max-message-per-msecs nil
                   :fn (fn [logdata]
                         (spit filename (with-out-str (stdoutfn logdata))
                               :append true))}]
    (timbre/set-config! [:appenders :file-appender] fappender)))

(defn stop-file-logging
  "Stops the file logging mechanism."
  []
  (timbre/set-config! [:appenders :file-appender] nil))

(defn setup []
  (start-file-logging LOG-FILE))
