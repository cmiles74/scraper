# Scraper

This project provides a web scraping library built around the JavaFX
[WebEngine][0], which in turn is built on top of [WebKit][1]. The goal of
this project is to provide an robust and easy-to-use web scraper that
doesn't require an external binary in order to function. With the
introduction of Java 8, this is finally beginning to seem feasible.

If you find this code useful in any way, please feel free to...

<a href="https://www.buymeacoffee.com/cmiles74" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>

# Usage

It's still early days yet, this project hasn't reached the point where
we're releasing builds of the library. Still, you can checkout the
project and build it yourself.

````clojure
[com.nervestaple/scraper "0.1.0-SNAPSHOT"]
````

Probably more fun is to check out the project and then interact with
it directly via the REPL.

    $ cd scraper
    $ lein repl

From there it's easy to get a handle on a WebEngine instance and
scrape out some content.

````
user> (def we (scraper/get-web-engine))

#'user/we

user> (scraper/load-url we "http://twitch.nervestaple.com")
{:state :ready}

user> (scraper/load-artoo we)
{:state :ready}

user> (scraper/scrape we "h1" {:title "text"})

{"title" "Bishop: Makes Your Web Service Shiny"} {"title" "Why Is My Web Service
API Crappy?"} {"title" "All Your HBase Are Belong to Clojure"}) ({"title" "Work
In Progress"} {"title" "Linux Is All About Choices"} {"title" "Real Life Web App
Integration Testing (IT) with Spring"} {"title" "Bishop: Makes Your Web Service
Shiny"} {"title" "Why Is My Web Service API Crappy?"} {"title" "All Your HBase
Are Belong to Clojure"})
````

As you can see in the example above, the [Artoo.js][2] JavaScript
scraping library is injected into the loaded page in order to make
your scraping easier. You are welcome! ;-)

If you're interested in being able to see the content that your
WebEngine instance is loading, you can get a handle on a WebView
instead. This will bring up a new window displaying the WebView.

````
user> (def wv (scraper/get-web-view))

#'user/wv

user> (def w (:web-engine wv))

#'user/we
````

You can also open up a JavaScript console (via [Firebug Lite][3]) in the
WebView, if needed.

````
user> (scraper/web-view-load-firebug wv)
"undefined"
````

Work on the project continues, but this should be enough to get you
started.

----

[0]:
http://docs.oracle.com/javafx/2/api/javafx/scene/web/WebEngine.html "Web Engine API"
[1]: http://en.wikipedia.org/wiki/WebKit "WebKit"
[2]: http://medialab.github.io/artoo "Artoo.js"
[3]: https://getfirebug.com/firebuglite "Firebug Lite"
