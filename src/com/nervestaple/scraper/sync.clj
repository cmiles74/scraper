(ns com.nervestaple.scraper.sync
  (:require [com.nervestaple.scraper.core :as scraper]
            [com.nervestaple.scraper.artoo :as artoo]
            [taoensso.timbre :as timbre
             :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async])
  (:import [javafx.concurrent Worker$State]))

(defn load-url [web-engine-map url]
  "Uses the provided web engine map to load the provided URL. Blocks
  until the URL has been successfully loaded or the web engine
  instance provides an error condition."
  (let [result-channel (scraper/load-url web-engine-map url)]
    (loop [state (async/<!! result-channel)]
      (timbre/info "STATE: " state)
      (if (not (= (:new state) Worker$State/SUCCEEDED))
        (recur (async/<!! result-channel))
        state))))

(defn fetch-from-channel
  "Blocks until a message arrives in the channel, then returns that
  message. If that message is an instance of Exception, it will be
  thrown."
  [channel]
  (let [item (async/<!! channel)]
    (if (instance? Exception item)
      (throw item)
      item)))

(defn get-web-engine []
  (fetch-from-channel (scraper/get-web-engine)))

(defn run-js [web-engine-map js]
  (fetch-from-channel (scraper/run-js web-engine-map js)))

(defn run-js-json [web-engine-map js]
  (fetch-from-channel (scraper/run-js-json web-engine-map js)))

(defn get-html [web-engine-map]
  (fetch-from-channel (scraper/get-html web-engine-map)))

(defn load-artoo [weeb-engine-map]
  (fetch-from-channel (artoo/load-artoo weeb-engine-map)))
