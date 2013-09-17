(ns postbin.main
  (:use [compojure.route :only [files not-found]]
        [compojure.handler :only [site]]
        [compojure.core :only [defroutes GET POST context]]
        [clojure.data.json :only [json-str read-json]]
        clostache.parser
        org.httpkit.server))

(defn- now [] (quot (System/currentTimeMillis) 1000))


(let [max-id (atom 0)]
  (defn next-id []
    (swap! max-id inc)))

(def clients (atom {}))
(def messages (ref [{:id (next-id)
                         :time (now)
                         :msg "system init"}]))

;; request handlers
(defn show-root-page [req]
  (render (slurp "./templates/index.html") {:title "post stream", :init-data (json-str @messages)} {:head (slurp "./templates/head.html")}))

(defn show-404 [req]
  (render (slurp "./templates/404.html") {:title "404 error"} {:head (slurp "./templates/head.html")}))

(defn receive-message [data]
  (let [message {:time (now) :id (next-id) :msg data}]
    (dosync
      (let [messages* (conj @messages message)
            total (count messages*)]
        (if (> total 1000)
          (ref-set messages (vec (drop (- total 1000) messages*)))
          (ref-set messages messages*))))
    (doseq [client (keys @clients)]
      (send! client (json-str {:type "delta" :data (list message)})))))

(defn delete-all []
  (dosync
    (ref-set messages (list)))
  (doseq [client (keys @clients)]
      (send! client (json-str {:type "purge"}))))

(defn ingest-data [req]
  (let [data (-> req :body)]
    (when (not (nil? data))
      (receive-message (apply str (map char (.bytes data)))))
  ""))

(defn receive-command [command]
  (let [cmd-type (-> command :type)]
    (cond
      (= "message" cmd-type) (receive-message (-> command :data))
      (= "delete" cmd-type) (delete-all))))
  
(defn async-handler [request]
  (with-channel request channel
    ;; handles both long polling and websockets
    (swap! clients assoc channel true)
    (on-receive channel (fn [data]
                          (receive-command (read-json data))))
    (on-close channel (fn [status]
                        (swap! clients dissoc channel)))))

(defroutes routes
  (GET "/" [] show-root-page)
  (POST "/post" [] ingest-data)
  (GET "/ws" [] async-handler)
  (files "/static/")
  (not-found show-404))

(defn -main []
  (let [port (Integer/parseInt (get (System/getenv) "VCAP_APP_PORT" "8080"))]
    (run-server (site #'routes) {:port port})))
