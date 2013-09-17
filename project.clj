(defproject postbin "1.0.0-SNAPSHOT"
  :description "Listens for payloads on one end, display them on another"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [http-kit "2.1.10"]
                 [compojure "1.1.5"]]
  :main postbin.main)
