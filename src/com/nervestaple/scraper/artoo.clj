(ns com.nervestaple.scraper.artoo
  (:require [com.nervestaple.scraper.core :as scraper]
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

(defn load-artoo [web-engine-map]
  "Injects the Artoo.js scraper into the provided WebEngine instance."
  (let [web-engine (:web-engine web-engine-map)]
    (scraper/run-js web-engine-map LOAD_ARTOO)))
