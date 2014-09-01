(ns com.nervestaple.scraper.core
  (:require [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]
            [cheshire.core :as json])
  (:import [javax.swing JFrame SwingUtilities]
           [javafx.application Application Platform]
           [javafx.embed.swing JFXPanel]
           [javafx.beans.value ChangeListener]
           [javafx.scene.web WebView]
           [javafx.concurrent Worker$State]))

(defmacro jfx-run
  "Invokes the provided body in the context of the JavaFX application thread."
  [& body]
  `(Platform/runLater (fn [] ~body)))

(defn jfx-init
  "Initializes the JavaFX environment."
  []
  (let [fxpanel (JFXPanel.)]
    (Platform/setImplicitExit false)
    (jfx-run (WebView.))
    true))

;; ensure that the JavaFX environment has been initialized
(defonce jfx-init-complete (jfx-init))

(defn get-web-engine
  "Returns a channel that will contain a new WebEngine instance after
  initialization."
  []
  (let [product-channel (async/chan)]
    (jfx-run
     (let [web-engine (javafx.scene.web.WebEngine.)
           load-status (async/chan (async/buffer 25))]
       (.addListener (.stateProperty (.getLoadWorker web-engine))
                     (proxy [ChangeListener] []
                       (changed [value previous new]
                         (async/go (async/>! load-status
                                             {:value value
                                              :previous previous
                                              :new new})))))
       (async/go (async/>! product-channel
                           {:web-engine web-engine :load-channel load-status})
                 (async/close! product-channel))))
    product-channel))

(defn load-url
  "Loads the provided URL in the WebEngine instance, returns a channel
  that may be monitored to track to status of the loading. The channel
  will be populated with JavaFX Worker$State instances."
  [web-engine-map url]
  (let [state-channel (async/chan (async/buffer 25))]
    (jfx-run
     (timbre/info "Loading " url)
     (let [web-engine (:web-engine web-engine-map)]
       (.load web-engine url)
       (async/go (loop []
                   (when-let [state (async/<! (:load-channel web-engine-map))]
                     (timbre/debug "Loading: " (:new state))
                     (async/>! state-channel state)
                     (if (= (:new state) Worker$State/SUCCEEDED)
                       (async/close! state-channel)
                       (recur))))
                 (async/close! state-channel))))
    state-channel))

(defn run-js
  "Executes the provided JavaScript code in the context of the
  WebEngine instance. This function returns a channel that will be
  populated with the result object."
  [web-engine-map js]
  (let [result-channel (async/chan)
        web-engine (:web-engine web-engine-map)]
    (jfx-run
     (try
       (timbre/debug "Executing JS: " + js)
       (let [result (.executeScript web-engine js)]
         (timbre/debug "RESULT: " (pr-str result))
         (async/go
           (async/>! result-channel result)
           (async/close! result-channel)))
       (catch Exception exception
         (timbre/warn exception "Failed to execute JS: " js)
         (async/go (async/>! result-channel exception)
                   (async/close! result-channel)))))
    result-channel))

(defn run-js-json
  "Executes the provided JavaScript code in the context of the
  WebEngine instance, the result is wrapped in a call to
  'JSON.stringify'. When the results are received, they will be parsed
  into a Clojure data structure. This function returns a channel that
  will be populated with the result object."
  [web-engine-map js]
  (let [result-channel (async/chan)
        web-engine (:web-engine web-engine-map)]
    (jfx-run
     (try
       (timbre/debug "Executing JS: " + js)
       (let [result (json/parse-string (.executeScript web-engine
                                                       (str "JSON.stringify(" js ")")))]
         (timbre/debug "RESULT: " (pr-str result))
         (async/go
           (async/>! result-channel result)
           (async/close! result-channel)))
       (catch Exception exception
         (timbre/warn exception "Failed to execute JS: " js)
         (async/go (async/>! result-channel exception)
                   (async/close! result-channel)))))
    result-channel))

(defn get-html [web-engine-map]
  "Fetches the HTML content of the current view. This function returns
  a channel that will be populated with the HTML content."
  (run-js web-engine-map "document.documentElement.outerHTML"))
