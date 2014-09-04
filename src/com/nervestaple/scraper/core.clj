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
  `(Platform/runLater (fn [] ~@body)))

(defn jfx-init
  "Initializes the JavaFX environment."
  []
  (let [fxpanel (JFXPanel.)]
    (Platform/setImplicitExit false)
    (jfx-run (WebView.))
    true))

;; ensure that the JavaFX environment has been initialized
(defonce jfx-init-complete (jfx-init))

(defn add-worker-listener
  "Listens to the load worker of the provided web engine instance and
  calls the appropriate handler function from the provided map as the
  content is loaded. The keys in the handler-fn-map should
  be

    :scheduled, :succeeded, :cancelled

  Each handler function should accept the following arguments:

    web-engine, map of worker states"
  [web-engine handler-fn-map]
  (.addListener (.stateProperty (.getLoadWorker web-engine))
                (proxy [ChangeListener] []
                  (changed [value previous new]

                    (let [state-map {:value value
                                     :previous previous
                                     :new new}]

                      (cond
                       (= new Worker$State/SCHEDULED)
                       (if (:scheduled handler-fn-map)
                         ((:scheduled handler-fn-map) web-engine state-map))

                       (= new Worker$State/SUCCEEDED)
                       (if (:succeeded handler-fn-map)
                         ((:succeeded handler-fn-map) web-engine state-map))

                       (= new Worker$State/CANCELLED)
                       (if (:cancelled handler-fn-map)
                         ((:cancelled handler-fn-map) web-engine state-map))))))))

(defn get-web-engine
  "Returns a channel that will contain a new WebEngine instance after
  initialization."
  []
  (let [result-channel (async/chan)]

    ;; setup our web engine instance
    (jfx-run
     (let [web-engine (javafx.scene.web.WebEngine.)
           load-status (async/chan (async/buffer 25))
           handler-fn (fn [web-engine state-map]
                        (async/go (async/>! load-status state-map)))]

       ;; pass worker state changes into our channel
       (add-worker-listener web-engine {:scheduled handler-fn
                                        :succeeded handler-fn
                                        :cancelled handler-fn})

       ;; place our web engine and load status channel in our result channel
       (async/go (async/>! result-channel
                           {:web-engine web-engine :load-channel load-status})
                 (async/close! result-channel))))
    result-channel))

(defn load-url
  "Loads the provided URL in the WebEngine instance, returns a channel
  that may be monitored to track to status of the loading. The channel
  will be populated with JavaFX Worker$State instances."
  [web-engine-map url]
  (let [result-channel (async/chan (async/buffer 25))]
    (jfx-run
     (timbre/debug "Loading " url)
     (let [web-engine (:web-engine web-engine-map)]
       (.load web-engine url)
       (async/go (loop []
                   (when-let [state (async/<! (:load-channel web-engine-map))]
                     (timbre/debug "Loading: " (:new state))
                     (async/>! result-channel state)
                     (cond
                      (= (:new state) Worker$State/SUCCEEDED)
                      (async/close! result-channel)

                      (= (:new state) Worker$State/CANCELLED)
                      (do (async/close! result-channel)
                          (let [exception (.getException (.getLoadWorker web-engine))]
                            (if exception (async/>! result-channel exception))))

                      :else (recur))))
                 (async/close! result-channel))))
    result-channel))

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
