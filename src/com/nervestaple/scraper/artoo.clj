(ns com.nervestaple.scraper.artoo
  (:require [com.nervestaple.scraper.core :as core]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]))

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

;; Javascript function to wait for a condition, cribbed from the PhantomJS project
(def JS-WAIT-FOR-FN
  "function waitFor(testFx, onReady, timeOutMillis) {
   var maxtimeOutMillis = timeOutMillis ? timeOutMillis : 3000, //< Default Max Timout is 3s
        start = new Date().getTime(),
        condition = false,
        interval = setInterval(function() {
            if ( (new Date().getTime() - start < maxtimeOutMillis) && !condition ) {
                // If not time-out yet and condition not yet fulfilled
                condition = (typeof(testFx) === \"string\" ? eval(testFx) : testFx()); //< defensive code
            } else {
                if(!condition) {
                    // If condition still not fulfilled (timeout but condition is 'false')
                    console.log(\"'waitFor()' timeout\");
                    clearInterval(interval);
                } else {
                    // Condition fulfilled (timeout and/or condition is 'true')
                    console.log(\"'waitFor()' finished in \" + (new Date().getTime() - start) + \"ms.\");
                    typeof(onReady) === \"string\" ? eval(onReady) : onReady();
                    //< Do what it's supposed to do once the condition is fulfilled
                    clearInterval(interval); //< Stop this interval
                }
            }
        }, 250); //< repeat check every 250ms
   };")


(defn wait-for
  "Waits for the provided js-test-fn to evaluate true and then invokes
  the ready-fn with no parameters."
  [crawler js-test-fn ready-fn]
  (let [window (async/<!! (core/run-js crawler "window"))
        ready-fn-name (str "readyFn" (.getTime (java.util.Date.)))]
    (.setMember window ready-fn-name ready-fn)
    (core/run-js crawler
                 (str "waitFor(function(){return " js-test-fn ";},"
                      "function(){" ready-fn-name ".run();});"))))

(defn load-artoo
  "Injects the Artoo.js scraper into the provided WebEngine instance."
  [web-engine-map]
  (let [web-engine (:web-engine web-engine-map)
        result-channel (async/chan)]

    (async/go

      ;; load artoo.js
      (let [channel (core/run-js web-engine-map LOAD_ARTOO)]
        (async/<! channel))

      ;; load our wait function
      (let [channel (core/run-js web-engine-map JS-WAIT-FOR-FN)]
        (async/<! channel))

      ;; wait for artoo to startup
      (wait-for web-engine-map
                "typeof artoo != \"undefined\""
                (fn []
                  (async/go (async/>! result-channel {:state :ready})
                            (async/close! result-channel)))))
    result-channel))

(defn scrape
    "Scrapes data from the currently loaded page in the provided web
  engine map. The selector represents the 'root iterator' used by
  artoo to select data, i.e.:

    td.title:has(a):not(:last)

  The artoo-map should be a data structure that will be converted to
  JSON and represents the data model of scraped content. For instance:

    {:title {:sel \"a\"}
     :url {:sel \"a\" :attr \"href\"}}"
    [web-engine-map selector artoo-model]
  (core/run-js-json web-engine-map
                    (str "artoo.scrape(\"" selector "\","
                         (json/generate-string artoo-model)")")))
