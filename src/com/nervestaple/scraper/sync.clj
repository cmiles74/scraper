(ns com.nervestaple.scraper.sync
  (:require [com.nervestaple.scraper.core :as core]
            [com.nervestaple.scraper.artoo :as artoo]
            [com.nervestaple.scraper.gui :as gui]
            [taoensso.timbre :as timbre
             :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async])
  (:import [javafx.concurrent Worker$State]))

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
  (fetch-from-channel (core/get-web-engine)))

(defn run-js [web-engine-map js]
  (fetch-from-channel (core/run-js web-engine-map js)))

(defn run-js-json [web-engine-map js]
  (fetch-from-channel (core/run-js-json web-engine-map js)))

(defn get-html [web-engine-map]
  (fetch-from-channel (core/get-html web-engine-map)))

(defn load-artoo [weeb-engine-map]
  (artoo/load-artoo weeb-engine-map))

(defn scrape [web-engine-map selector artoo-map]
  (fetch-from-channel (artoo/scrape web-engine-map selector artoo-map)))

(defn get-web-view []
  (fetch-from-channel (gui/get-web-view)))

(defn web-view-load-firebug [web-view-map]
  (fetch-from-channel (gui/load-firebug web-view-map)))

(defn load-url [web-engine-map url]
  "Uses the provided web engine map to load the provided URL. Blocks
  until the URL has been successfully loaded or the web engine
  instance provides an error condition. If the web engine loads
  successfully, injects the artoo.js scraper library into the web
  engine instance."
  (let [result-channel (core/load-url web-engine-map url)]
    (loop [state (async/<!! result-channel)]
      (timbre/debug "STATE: " state)
      (if (not (= (:new state) Worker$State/SUCCEEDED))
        (recur (async/<!! result-channel))
        (do (load-artoo web-engine-map)
            state)))))
