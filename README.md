# postbin

Minimal client and server app that shows whatever is posted to it on a web interface.

This project requires both client and server to be able to use WebSockets.

When the server is running, point your browser to the root of the server.
This will serve a dashboard of posts.

Data can be sent to the server via 2 endpoints:

* /post which takes a UTF-8 encoded text payload
* /ws which takes a JSON payload

## Local setup

Install [Leiningen](https://github.com/technomancy/leiningen#readme) and run:

    lein deps
    lein run

## Deploying to Stackato

    lein deps  # this is not automatically run on the server yet.
    stackato push -n

