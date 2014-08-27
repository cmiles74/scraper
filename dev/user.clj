(ns user
  (:use [clojure.repl])
  (:require [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [scraper.core :as scraper])
  (:import [javafx.concurrent Worker$State]))

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

(defn get-web-engine []
  (async/<!! (scraper/get-web-engine)))

(defn load-url [web-engine-map url]
  (let [result-channel (scraper/load-url web-engine-map url)]
    (loop [state (async/<!! result-channel)]
      (timbre/info "STATE: " state)
      (if (not (= (:new state) Worker$State/SUCCEEDED))
        (recur (async/<!! result-channel))
        state))))

(defn run-js [web-engine-map js]
  (async/<!! (scraper/run-js web-engine-map js)))

(defn run-js-json [web-engine-map js]
  (async/<!! (scraper/run-js-json web-engine-map js)))

(defn get-html [web-engine]
  (run-js web-engine "document.documentElement.outerHTML"))
