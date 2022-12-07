(ns gback.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [dk.ative.docjure.spreadsheet :as doc]
            [reitit.ring :as ring]
            [reitit.http :as http]
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
  (format "%.2f" (* 100 (float (/ enrolled eligible)))))

(defn- load-state-level-data
  "Selects columns of interest from workbook"
  [workbook]
  (->> workbook
       (doc/select-sheet "State Level Tables From Report")
       (doc/select-columns {:B :state :E :eligible :H :enrolled})
       (remove #(or
                  (= "SBM Subtotal" (:state %))
                  (= "StateName" (:state %))
                  (nil? (:state %))))
       (map #(assoc % :enrolled-percentage (calculate-percentage %)))))

(defn get-marketplace-data
  [_]
  (try
    (let [state-level-data (load-state-level-data (fetch-workbook))]
      {:status 200 :body (json/write-str state-level-data)})
    (catch Exception e
      (log/error "Exception: " e)
      {:status 500 :body "Unhandled Exception"})))

(def ^:private headers
  {"Access-Control-Allow-Origin"  "http://localhost:8281"
   "Access-Control-Allow-Headers" "*"
   "Access-Control-Allow-Methods" "GET"
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
      {:get {:handler (wrap-headers get-marketplace-data)}}]]]])

(def app
  (http/ring-handler
    (http/router
      routes)
    (ring/routes
      (ring/create-default-handler))
    {:executor   sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async true})
  (println "server running in port 3000"))
