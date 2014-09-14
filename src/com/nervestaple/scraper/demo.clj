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

;; the root of all amazon.com URLs
(def DOMAIN-ROOT "http://www.amazon.com")

;; The start of our crawl, search for USB flash drives
(def SEED-URL (str DOMAIN-ROOT
                   "/s/ref=sr_pg_1?rh=i%3Aaps%2Ck%3Ausb+drive+flash+drive"
                   "&keywords=usb+drive+flash+drive"))

(defn scrape-links
  "Loads the specified URL in the provided web-engine-map instance and
  then scrapes the result data from the loaded page. Returns a channel
  that will contain the scraped results."
  [crawler url]

  ;; load the amazon result page
  (sync/load-url crawler url)
  (sync/load-artoo crawler)

  ;; create our channel and scrape our data
  (let [result-channel (async/chan)
        items (sync/scrape crawler ".results > div"
                           {:title {:sel "h3 a"}
                            :link {:sel "a" :attr "href"}
                            :rating {:sel ".asinReviewsSummary > a:eq(1)"
                                     :attr "alt"}
                            :by {:sel "h3 span:eq(1)"}
                            :price {:sel "ul a span.price"}
                            :dept {:sel "li.seeAll span"}})]

    ;; add the scraped results to our output channel
    (async/go (async/>! result-channel items))

    result-channel))

(defn scrape-next-page
  "Scrapes the link to the next page of search results from the
  provided web-engine-map instance. Returns a channel that will
  contain the link to the next page."
  [crawler]

  ;; setup our output channel
  (let [result-channel (async/chan)]

    ;; wait for the next page link to apper
    (artoo/wait-for crawler
                    "artoo.$('.pagnRA a').size() > 0"

                    ;; scrape the link to the next page
                    (let [page-channel (artoo/scrape crawler ".pagnRA" {:sel "a" :attr "href"})]

                      ;; add the link to our output channel
                      (async/go
                        (async/>! result-channel (first (async/<! page-channel))))))
    result-channel))

(defn scrape-amazon
  "Creates a new web-engine-map instance and begins crawling the seed
  URL for search results. Returns an output channel that will contain
  pages of search result data. If an output channel, web-engine-map
  and URL are provided then the provided URL will be loaded, scraped
  and the results added to the output channel."
  ([]

     ;; create our output channel and crawler
     (let [link-channel (async/chan)
           crawler (sync/get-web-engine)]

       ;; start scraping the seed URL
       (scrape-amazon link-channel crawler SEED-URL)))

  ([link-channel crawler url]

     ;; scrape the links from the provided URL
     (let [scrape-channel (scrape-links crawler url)]
       (timbre/info "Scraping" url)

       ;; add our scraped data to our output channel
       (async/go
         (let [scrape-results (async/<! scrape-channel)]
           (async/>! link-channel scrape-results))

         ;; scrape the next page link
         (let [page-link (async/<! (scrape-next-page crawler))]

           ;; if we have a next page link, scrape that page next
           (if (not (nil? page-link))
             (scrape-amazon link-channel crawler (str DOMAIN-ROOT page-link))

             ;; no next page, end the crawl
             (do (async/close! link-channel)
                 (timbre/info "End of Amazon crawl, all results collected"))))))

     link-channel))

(defn collect-amazon-data
  "Scrapes Amazon.com for search result data. Returns an atom that
  will accumulate the search results."
  []

  ;; create our accumulator atom and start scraping
  (let [data (atom [])
        result-channel (scrape-amazon)]

    ;; read scrape results and add them to our accumulator
    (async/go-loop [data-page (async/<! result-channel)]
      (dosync (swap! data into data-page))
      (timbre/info "Collected" (count @data) "links")

      ;; if we have a page of data, loop for another one
      (if (seq data-page)
        (recur (async/<! result-channel))))

    data))
