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

(def socket-clients (atom {}))
(def longpoll-clients (atom {}))
(def messages (ref [{:id (next-id)
                         :time (now)
                         :msg "system init"}]))

;; request handlers
(defn show-root-page [req]
  (render (slurp "./templates/index.html")
          {:title "post stream", :init-data (json-str @messages)}
          {:head (slurp "./templates/head.html")}))

(defn show-404 [req]
  (render (slurp "./templates/404.html")
          {:title "404 error"}
          {:head (slurp "./templates/head.html")}))

(defn json-error [code channel]
  (let [json-message (json-str {:type "error" :data code})]
    (send! channel json-message)
    (send! channel {:status code
                    :headers {"Content-Type" "application/json; charset=utf-8"}
                    :body json-message})))

(defn receive-message [data]
  (let [message {:time (now) :id (next-id) :msg data}]
    (dosync
      (let [messages* (conj @messages message)
            total (count messages*)]
        (if (> total 1000)
          (ref-set messages (vec (drop (- total 1000) messages*)))
          (ref-set messages messages*))))
    (let [json-message (json-str {:type "delta" :data (list message)})]
      (doseq [client (keys @socket-clients)]
        (send! client json-message))
      (doseq [client (keys @longpoll-clients)]
        (send! client {:status 200
                       :headers {"Content-Type" "application/json; charset=utf-8"}
                       :body json-message})))))

(defn get-delta-data [last-id channel]
  (let [data (filter (fn[elem]
                       (< last-id (-> elem :id)))
                     @messages)]
    (let [json-message (json-str {:type "delta" :data data})]
      (if (websocket? channel)
        (send! channel json-message)
        (send! channel {:status 200
                        :headers {"Content-Type" "application/json; charset=utf-8"}
                        :body json-message})))))

(defn get-all-data [channel]
  (let [json-message (json-str {:type "data" :data @messages})]
    (if (websocket? channel)
      (send! channel json-message)
      (send! channel {:status 200
                      :headers {"Content-Type" "application/json; charset=utf-8"}
                      :body json-message}))))

(defn delete-all [channel]
  (dosync
    (ref-set messages (list)))
  (let [json-message (json-str {:type "purge"})]
    (doseq [client (keys @socket-clients)]
      (send! client (json-str {:type "purge"})))
    (doseq [client2 (keys @longpoll-clients)]
      (send! client2 {:status 200
                     :headers {"Content-Type" "application/json; charset=utf-8"}
                     :body json-message}))
    (if (not (or (websocket? channel) (contains? @longpoll-clients channel)))
      (do
        ;; neither websocket not longpoll, must be xhr
        (send! channel {:status 200
                        :headers {"Content-Type" "application/json; charset=utf-8"}
                        :body json-message})))))

(defn ingest-data [req]
  (let [data (:body req)]
    (when (not (nil? data))
      (receive-message (String. (.bytes data))))
  ""))

(defn receive-command [command channel]
  (let [cmd-type (:type command)]
    (cond
      (= "message" cmd-type) (receive-message (:data command))
      (= "fetch-from" cmd-type) (get-delta-data (:data command) channel)
      (= "fetch-all" cmd-type) (get-all-data channel)
      (= "delete" cmd-type) (delete-all channel)
      :else (json-error 400 channel))))
  
(defn async-handler [request]
  (with-channel request channel
    ;; handles both long polling and websockets
    (if (websocket? channel)
      (do
        (swap! socket-clients assoc channel true)
        (on-close channel (fn [status]
                            (swap! socket-clients dissoc channel)))
        (on-receive channel (fn [data]
                              (receive-command (read-json data) channel))))
      (do
        (swap! longpoll-clients assoc channel true)
        (on-close channel (fn [status]
                            (swap! longpoll-clients dissoc channel)))))))

(defn xhr-command [request]
  (with-channel request channel
    (let [data (:body request)]
      (when (not (nil? data))
        (receive-command (read-json (String. (.bytes data))) channel)))))

(defroutes routes
  (GET "/" [] show-root-page)
  (POST "/post" [] ingest-data)
  (GET "/ws" [] async-handler)
  (POST "/xhr" [] xhr-command)
  (files "/static/")
  (not-found show-404))

(defn -main []
  (let [port (Integer/parseInt (get (System/getenv) "VCAP_APP_PORT" "8080"))]
    (run-server (site #'routes) {:port port})))
