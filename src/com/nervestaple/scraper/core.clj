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
  `(if (Platform/isFxApplicationThread)
     (try ~@body
          (catch Exception exception#
            (timbre/warn "Exception in JFX Application thread: " exception#)
            (timbre/debug exception#)))
     (Platform/runLater (fn []
                          (try ~@body
                               (catch Exception exception#
                                 (timbre/warn "Exception in JFX Application thread: " exception#)
                                 (timbre/debug exception#)))))))

(defn jfx-init
  "Initializes the JavaFX environment."
  []
  (let [fxpanel (JFXPanel.)]
    (Platform/setImplicitExit false)
    (jfx-run (WebView.))
    true))

;; ensure that the JavaFX environment has been initialized
(defonce jfx-init-complete (jfx-init))

(defn add-change-listener
  [observable-value handler-fn]
  (.addListener observable-value
                (proxy [ChangeListener] []
                  (changed [value previous new]
                    (let [state-map {:value value
                                     :previous previous
                                     :new new}]
                      (timbre/trace "ChangeListener invoked for "
                                    (class observable-value) ": " state-map)
                      (handler-fn state-map))))))

(defn add-worker-state-listener
  "Listens to the provided ObservableValue that emits Worker$State
  values and invokes the appropriate handler function from the
  handler-fn-map as the state of the ObservableValue changes. Behind
  the scenes a ChangeListener is instantiated and that object invokes
  the handler functions. The handler-fn-map should contain the
  following keys, these correspond to the various states of
  Worker$State:

    :scheduled, :succeeded, :cancelled

  Each key's value should be a function that will accept one argument,
  a map. That map will contain the following keys:

    :value, :previous, :new

  The values for those keys will be the ObservableValue instance at
  it's current, previous and it's new state."
  [observable-value handler-fn-map]
  (add-change-listener observable-value
                       #(cond
                         (= (:new %) Worker$State/SCHEDULED)
                         (if (:scheduled handler-fn-map)
                           ((:scheduled handler-fn-map) %))

                         (= (:new %) Worker$State/SUCCEEDED)
                         (if (:succeeded handler-fn-map)
                           ((:succeeded handler-fn-map) %))

                         (= (:new %) Worker$State/CANCELLED)
                         (if (:cancelled handler-fn-map)
                           ((:cancelled handler-fn-map) %)))))

(defn get-web-engine
  "Returns a channel that will contain a new WebEngine instance after
  initialization."
  []
  (let [result-channel (async/chan)]

    ;; setup our web engine instance
    (jfx-run
     (let [web-engine (javafx.scene.web.WebEngine.)
           load-status (async/chan (async/buffer 25))]

       ;; pass worker state changes into our channel
       (add-worker-state-listener (.stateProperty (.getLoadWorker web-engine))
                                  {:scheduled #(async/go (async/>! load-status %))
                                   :cancelled #(async/go (async/>! load-status %))
                                   :succeeded #(async/go (async/>! load-status %))})

       ;; place our web engine and load status channel in our result channel
       (async/go (async/>! result-channel
                           {:web-engine web-engine :load-channel load-status})
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
       (timbre/trace "Executing JS: " + js)
       (let [result (.executeScript web-engine js)]
         (timbre/trace "RESULT: " (pr-str result))
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
       (timbre/trace "Executing JS: " + js)
       (let [result (json/parse-string (.executeScript web-engine
                                                       (str "JSON.stringify(" js ")")))]
         (timbre/trace "RESULT: " (pr-str result))
         (async/go
           (async/>! result-channel result)
           (async/close! result-channel)))
       (catch Exception exception
         (timbre/warn exception "Failed to execute JS: " js)
         (async/go (async/>! result-channel exception)
                   (async/close! result-channel)))))
    result-channel))

(defn load-url
  "Loads the provided URL in the WebEngine instance, returns a channel
  that may be monitored to track to status of the loading. The channel
  will be populated with JavaFX Worker$State instances."
  [web-engine-map url]
  (let [result-channel (async/chan (async/buffer 25))]
    (jfx-run
     (timbre/trace "Loading " url)
     (let [web-engine (:web-engine web-engine-map)]
       (.load web-engine url)
       (async/go (loop []
                   (when-let [state (async/<! (:load-channel web-engine-map))]
                     (timbre/trace "Loading: " (:new state))
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

(defn get-html [web-engine-map]
  "Fetches the HTML content of the current view. This function returns
  a channel that will be populated with the HTML content."
  (run-js web-engine-map "document.documentElement.outerHTML"))
