(ns com.nervestaple.scraper.demo
  (:use [clojure.repl])
  (:require [taoensso.timbre :as timbre
               :only (trace debug info warn error fatal spy)]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [com.nervestaple.scraper.core :as core]
            [com.nervestaple.scraper.sync :as sync]
            [com.nervestaple.scraper.artoo :as artoo])
  (:import [javafx.concurrent Worker$State]))

(def DOMAIN-ROOT "http://www.amazon.com")
(def SEED-URL (str DOMAIN-ROOT "/s/ref=sr_pg_1?rh=i%3Aaps%2Ck%3Ausb+drive+flash+drive&keywords=usb+drive+flash+drive"))

;; javascript function to wait for a condition, cribbed from the PhantomJS project
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
  [crawler js-test-fn ready-fn]
  (let [window (sync/run-js crawler "window")]
    (.setMember window "readyFn" ready-fn)
    (sync/run-js crawler (str "waitFor(" js-test-fn ",function(){readyFn.run();});"))))

(defn scrape-links
  ([]
     (let [link-channel (async/chan)
           crawler (sync/get-web-engine)]
       (scrape-links link-channel crawler SEED-URL)))

  ([link-channel crawler url]
     (let [load-result (sync/load-url crawler url)]
       (if (= Worker$State/SUCCEEDED (:new load-result))
         (let [window (sync/run-js crawler "window")]

           ;; inject our waitFor function
           (sync/run-js crawler JS-WAIT-FOR-FN)

           ;; wait for the results table to load, then invoke our ready function
           (wait-for crawler
                     "function(){return artoo.$('.results > div').size() > 0;}"
                     (fn []

                       ;; scrape the items and add them to our channel of links
                       (let [items (sync/scrape crawler ".results > div"
                                                {:title {:sel "h3 a"}
                                                 :link {:sel "a" :attr "href"}
                                                 :rating {:sel ".asinReviewsSummary > a:eq(1)"
                                                          :attr "alt"}
                                                 :by {:sel "h3 span:eq(1)"}
                                                 :price {:sel "ul a span.price"}
                                                 :dept {:sel "li.seeAll span"}})]
                         (async/go (async/>! link-channel items)))

                       ;; get the next link and scrape it
                       (let [next-link (sync/scrape crawler ".pagnRA" {:sel "a" :attr "href"})]
                         (if (first next-link)
                           (scrape-links link-channel crawler (str DOMAIN-ROOT (first next-link)))
                           (do
                             (timbre/debug "End of crawl, no more results")
                             (async/close! link-channel)))))))
         (timbre/warn "Problem loading " url ": " load-result)))

     link-channel))
