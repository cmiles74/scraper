(ns scraper.core
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

;; Javascript to inject the Artoo scraper into loaded pages
(def LOAD_ARTOO "(function() {
    var t = {},
        e = !0;
    if (\"object\" == typeof this.artoo && (artoo.settings.reload || (artoo.log.verbose(\"artoo already exists within this page. No need to inject him again.\"), artoo.loadSettings(t), artoo.hooks.trigger(\"exec\"), e = !1)), e) {
        var o = document.getElementsByTagName(\"body\")[0];
        o || (o = document.createElement(\"body\"), document.firstChild.appendChild(o));
        var i = document.createElement(\"script\");
        console.log(\"artoo.js is loading...\"), i.src = \"//medialab.github.io/artoo/public/dist/artoo-latest.min.js\", i.type = \"text/javascript\", i.id = \"artoo_injected_script\", i.setAttribute(\"settings\", JSON.stringify(t)), o.appendChild(i)
    }
}).call(this);")

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

(defn load-artoo [web-engine-map]
  "Injects the Artoo.js scraper into the provided WebEngine instance."
  (let [web-engine (:web-engine web-engine-map)]
      (jfx-run
       (try
         (.executeScript web-engine LOAD_ARTOO)
         (timbre/info "Loaded artoo.js! :-D")
         (catch Exception exception
           (timbre/warn "Failed to load artoo.js: " exception))))))

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
                     (if (= (:new state) Worker$State/SUCCEEDED)
                       (load-artoo web-engine-map))
                     (async/>! state-channel state)
                     (recur)))
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
