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
user> (scraper/load-url we "http://news.ycombinator.com")
{:value #<ReadOnlyPropertyImpl ReadOnlyObjectProperty [bean:
javafx.scene.web.WebEngine$LoadWorker@191f0cc2, name: state, value: SUCCEEDED]>,
previous #<State RUNNING>, :new #<State SUCCEEDED>}
user> (scraper/load-artoo we)
"undefined"
user> (scraper/run-js-json we "artoo.scrape(
'td.title:has(a):not(:last)', {title: {sel: 'a'},
url: {sel: 'a', attr: 'href'}})")
({"title" "Standard Markdown", "url" "http://standardmarkdown.com/"} {"title"
"Perdue Says Its Hatching Chicks Are Off Antibiotics", "url"
"http://www.npr.org/blogs/thesalt/2014/09/03/345315380/perdue-says-its-
hatching-chicks-are-off-antibiotics?sc=tw"} {"title"
"Tesla selects Nevada for battery plant", "url" "
http://news.yahoo.com/ap-source-tesla-selects-nevada-battery-plant-200941469.html"}
{"title" "Generative eBook Covers", "url"
"http://www.nypl.org/blog/2014/09/03/generative-ebook-covers"} ...
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
