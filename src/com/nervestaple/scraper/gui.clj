(ns com.nervestaple.scraper.gui
  (:require [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [com.nervestaple.scraper.core :as core])
  (:import [javax.swing JFrame SwingUtilities]
           [javafx.scene.control ScrollPane]
           [javafx.scene Scene]
           [javafx.scene.layout BorderPane]
           [java.awt BorderLayout]
           [javafx.application Application Platform]
           [javafx.embed.swing JFXPanel]
           [javafx.beans.value ChangeListener]
           [javafx.scene.web WebView]
           [javafx.concurrent Worker$State]))

(def LOAD-FIREBUG-LITE
  "if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}")

(defn web-view-frame
  [url]
  (let [value-channel (async/chan)
        jframe (JFrame. "WebView")
        jfxpanel (JFXPanel.)]

    ;; setup our swing frame
    (doto jframe
      (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
      (.setLayout (BorderLayout.)))
    (.add (.getContentPane jframe) jfxpanel BorderLayout/CENTER)

    (SwingUtilities/invokeLater
     (fn []
       (core/jfx-run
        (fn []
          (let [borderpane (BorderPane.)
                scene (Scene. borderpane)
                web-view (WebView.)
                web-engine (.getEngine web-view)
                load-status (async/chan)]

            ;; listen for load status in the web engine
            (.addListener (.stateProperty (.getLoadWorker web-engine))
                          (proxy [ChangeListener] []
                            (changed [value previous new]
                              (async/go (async/>! load-status
                                                  {:value value
                                                   :previous previous
                                                   :new new})))))

            ;; setup our javafx panel
            (.setCenter borderpane web-view)
            (.setScene jfxpanel scene)
            (.load web-engine url)


            (async/go
              (async/>! value-channel {:frame jframe :web-view web-view
                                       :web-engine {:web-engine web-engine
                                                    :load-channel load-status}})
              (async/close! value-channel)))))
       (.setVisible jframe true)))
    value-channel))

(defn load-firebug
  [web-view-map]
  (let [web-engine-map (:web-engine web-view-map)]
    (core/run-js web-engine-map LOAD-FIREBUG-LITE)))
