(ns gback.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dk.ative.docjure.spreadsheet :as doc]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari :as sieppari]
            [ring.adapter.jetty :as jetty]))

(def ^:private WORKBOOK_URL "https://aspe.hhs.gov/sites/default/files/private/aspe-files/106941/workbook.xls")

(defn- fetch-workbook
  "Fetch xls workbook from catalog.data.gov/dataset and loads in memory"
  []
  (let [response (client/get WORKBOOK_URL {:as :byte-array :throw-exceptions false})]
    (with-open [xin (io/input-stream (:body response))]
      (doc/load-workbook xin))))

(defn- calculate-percentage
  [{:keys [enrolled eligible]}]
  (Math/round (* 100 (float (/ enrolled eligible)))))

(defn- load-state-level-data
  "Selects columns of interest from workbook"
  [workbook]
  (->> workbook
       (doc/select-sheet "State Level Tables From Report")
       (doc/select-columns {:B :state :E :eligible :H :enrolled})
       (remove #(or
                  (nil? (:state %))
                  (str/includes? (str/lower-case (:state %)) "total")
                  (= "StateName" (:state %))))
       (map #(assoc % :state (str/trim (:state %))))
       (map #(assoc % :enrolled-percentage (calculate-percentage %)))))

(defn get-marketplace-data
  [_]
  (try
    (let [state-level-data (load-state-level-data (fetch-workbook))]
      {:status 200 :body (json/write-str state-level-data)})
    (catch Exception e
      (log/error "Exception: " e)
      {:status 500 :body "Unhandled Exception"})))

(s/def ::state string?)
(s/def ::actual-percent-enrolled int?)
(s/def ::guessed-percent-enrolled int?)
(s/def ::guess
  (s/keys :req [::state
                ::actual-percent-enrolled
                ::guessed-percent-enrolled]))

(def ds (jdbc/get-datasource {:dbtype "mysql" :dbname "gback" :user "username" :password "password"}))

(def insert-guess-query
  "INSERT INTO Guesses(state, actual_percent_enrolled, guessed_percent_enrolled)
  VALUES (?,?,?)")

; TODO - test
(defn post-guess
  [guess]
  {:pre [(s/conform ::guess guess)]}
  (try
    {:status 201 :body (json/write-str "ok")}
    (let [{:keys [state actual-percent-enrolled guessed-percent-enrolled]} guess
          result (jdbc/execute-one! ds [insert-guess-query state actual-percent-enrolled guessed-percent-enrolled])
          _ (println "HERE BE RESULT: " result)]
      {:status 201 :body (json/write-str result)})
    (catch Exception e
      (log/error "Exception: " e)
      {:status 500 :body "Unhandled Exception"})))

(def ^:private headers
  {"Access-Control-Allow-Origin"  "http://localhost:8281"
   "Access-Control-Allow-Headers" "*"
   "Access-Control-Allow-Methods" #{"GET" "POST"}
   "Content-Type" "application/json"})

; TODO - reitit / muuntaja
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
      {:get {:handler (wrap-headers get-marketplace-data)}}]
     ["/guess"
      {:options {:handler (wrap-headers (fn [_] {:status 200}))}
       :post {:parameters {:body {:state string? :actual-percent-enrolled int? :guessed-percent-enrolled int?}}
              :handler (wrap-headers (fn [{:keys [body-params]}]
                                       (post-guess body-params)))}}]]]])

(def middleware
  {;:reitit.interceptor/transform dev/print-context-diffs ;; pretty context diffs
   ;;:validate spec/validate ;; enable spec validation for route data
   ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
   :data {:coercion     reitit.coercion.spec/coercion
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
