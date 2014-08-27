# Scraper

This project provides a web scraping library built around the JavaFX
[WebEngine][0], which in turn is built on top of [WebKit][1]. The goal of
this project is to provide an robust and easy-to-use web scraper that
doesn't require an external binary in order to function. With the
introduction of Java 8, this is finally beginning to seem feasible.

# Usage

It's still early days yet, this project hasn't reached the point where
we're releasing builds of the library. Still, you can checkout the
project and build it yourself.

````clojure
[scraper "0.1.0-SNAPSHOT"]
````

Probably more fun is to check out the project and then interact with
it directly via the REPL.

    $ cd scraper
	$ lein repl

From there it's easy to get a handle on a WebEngine instance and
scrape out some content.

````
user> (def w (get-web-engine))
#'user/w
user> (load-url w "http://twitch.nervestaple.com")
{:value #<ReadOnlyPropertyImpl ReadOnlyObjectProperty
  [bean: javafx.scene.web.WebEngine$LoadWorker@64a08a44, name: state,
    value: SUCCEEDED]>, :previous #<State RUNNING>,
	:new #<State SUCCEEDED>}
user> (run-js-json w "artoo.scrape('h1 > a', 'html')")
("Work In Progress" "Linux Is All About Choices" ""
"Real Life Web App Integration Testing (IT) with Spring" ""
"Bishop: Makes Your Web Service Shiny" "" "Why Is My Web Service API
Crappy?"
"" "All Your HBase Are Belong to Clojure")
````

As you can see in the example above, the [Artoo.js][2] JavaScript
scraping library is injected into the loaded page in order to make
your scraping easier. You are welcome! ;-)

----

[0]:
http://docs.oracle.com/javafx/2/api/javafx/scene/web/WebEngine.html "Web Engine API"
[1]: http://en.wikipedia.org/wiki/WebKit "WebKit"
[2]: http://medialab.github.io/artoo "Artoo.js"
