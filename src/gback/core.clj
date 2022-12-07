(ns gback.core
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [gback.services.db :as db]
            [gback.services.healthcare :as health]
            [muuntaja.core :as m]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari :as sieppari]
            [ring.adapter.jetty :as jetty]))

(defn- calculate-percentage
  [{:keys [enrolled eligible]}]
  (Math/round (* 100 (float (/ enrolled eligible)))))

(defn- load-state-level-data
  "Selects columns of interest from workbook"
  [workbook]
  (->> workbook
       health/select-data
       (remove #(or
                 (nil? (:state %))
                 (str/includes? (str/lower-case (:state %)) "total")
                 (= "StateName" (:state %))))
       (map #(assoc % :state (str/trim (:state %))))
       (map #(assoc % :enrolled-percentage (calculate-percentage %)))))

(defn handle-get-marketplace-data
  [_]
  (try
    (let [state-level-data (load-state-level-data (health/fetch-workbook))]
      {:status 200 :body (json/write-str state-level-data)})
    (catch Exception e
      (log/error "Exception: " e)
      {:status 500 :body "Unhandled Exception"})))

(s/def ::state string?)
(s/def ::actual-percent-enrolled int?)
(s/def ::guessed-percent-enrolled int?)
(s/def ::guess
  (s/keys :req-un [::state
                   ::actual-percent-enrolled
                   ::guessed-percent-enrolled]))

(defn handle-post-guess
  "Insert guess and return stats for related guesses"
  [guess]
  (if (s/valid? ::guess guess)
    (try
      (let [results (db/post-guess guess)]
        {:status 201 :body (json/write-str results)})
      (catch Exception e
        (log/error "Exception: " e)
        {:status 500 :body "Unhandled Exception"}))
    {:status 400 :body "Bad Request Body"}))

; TODO - fold into middleware
(def ^:private headers
  {"Access-Control-Allow-Origin"  #{"http://localhost:8280" "http://localhost:8281"}
   "Access-Control-Allow-Headers" "*"
   "Access-Control-Allow-Methods" #{"GET" "POST"}
   "Content-Type" "application/json"})

(defn- wrap-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response [:headers] merge headers))))

(def routes
  [["/health"
    {:get {:handler (fn [_] {:status 200 :body "ok"})}}]
   ["/api"
    ["/1"
     ["/marketplace"
      {:get {:handler (wrap-headers handle-get-marketplace-data)}}]
     ["/guess"
      {:options {:handler (wrap-headers (fn [_] {:status 200}))}
       :post {:parameters {:body {:state string? :actual-percent-enrolled int? :guessed-percent-enrolled int?}}
              :handler (wrap-headers (fn [{:keys [body-params]}]
                                       (handle-post-guess body-params)))}}]]]])

(def middleware
  {:data {:coercion     reitit.coercion.spec/coercion
          :muuntaja     m/instance
          :middleware [muuntaja/format-middleware
                       coercion/coerce-exceptions-middleware
                       coercion/coerce-request-middleware
                       coercion/coerce-response-middleware]}})

(def app
  (ring/ring-handler
   (ring/router
    routes
    middleware)
   (ring/routes
    (ring/create-default-handler))
   {:executor   sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async true})
  (println "server running in port 3000"))
