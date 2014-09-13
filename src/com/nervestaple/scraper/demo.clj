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

(defn scrape-links
  [crawler url]
  (sync/load-url crawler url)
  (sync/load-artoo crawler)
  (let [result-channel (async/chan)
        items (sync/scrape crawler ".results > div"
                           {:title {:sel "h3 a"}
                            :link {:sel "a" :attr "href"}
                            :rating {:sel ".asinReviewsSummary > a:eq(1)"
                                     :attr "alt"}
                            :by {:sel "h3 span:eq(1)"}
                            :price {:sel "ul a span.price"}
                            :dept {:sel "li.seeAll span"}})]
    (async/go (async/>! result-channel items))
    result-channel))

(defn scrape-next-page
  [crawler]
  (let [result-channel (async/chan)]
    (artoo/wait-for crawler
                    "artoo.$('.pagnRA a').size() > 0"
                    (let [page-channel (artoo/scrape crawler ".pagnRA" {:sel "a" :attr "href"})]
                      (async/go
                        (async/>! result-channel (first (async/<! page-channel))))))
    result-channel))

(defn scrape-amazon
  ([]
     (let [link-channel (async/chan)
           crawler (sync/get-web-engine)]
       (scrape-amazon link-channel crawler SEED-URL)))

  ([link-channel crawler url]
     (let [scrape-channel (scrape-links crawler url)]
       (timbre/info "Scraping" url)
       (async/go

         (let [scrape-results (async/<! scrape-channel)]
           (timbre/info "Scraped " (count scrape-results) "links")
           (async/>! link-channel scrape-results))

         (let [page-link (async/<! (scrape-next-page crawler))]
           (timbre/info "Scraped link to next page" page-link)
           (if (not (nil? page-link))
             (scrape-amazon link-channel crawler (str DOMAIN-ROOT page-link))
             (async/close! link-channel)))))
     link-channel))

(defn collect-amazon-data
  []
  (let [data (atom [])
        result-channel (scrape-amazon)]
    (async/go-loop [data-page (async/<! result-channel)]
      (if (seq data-page)
        (do
          (dosync
           (timbre/info "Collecting" (count data-page) "links")
           (swap! data into data-page))
          (timbre/info "Collected" (count @data) "links")
          (recur (async/<! result-channel)))))
    data))
