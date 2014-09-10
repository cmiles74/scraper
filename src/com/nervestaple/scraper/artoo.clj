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

(defn load-artoo
  "Injects the Artoo.js scraper into the provided WebEngine instance."
  [web-engine-map]
  (let [web-engine (:web-engine web-engine-map)]
    (core/run-js web-engine-map LOAD_ARTOO)))

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
