# postbin

Minimal app that shows whatever is posted to it on a web interface

## Local setup

Install [Leiningen](https://github.com/technomancy/leiningen#readme) and run:

    lein deps
    lein run

## Deploying to Stackato

    lein deps  # this is not automatically run on the server yet.
    stackato push -n

