(ns gback.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as doc]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [ring.adapter.jetty :as jetty])
  (:import (java.io ByteArrayOutputStream InputStream)))

(def ^:private WORKBOOK_URL "https://aspe.hhs.gov/sites/default/files/private/aspe-files/106941/workbook.xls")
(def ^:private TOTAL_COLUMN_HEADER "Total Eligible to Enroll in a Marketplace Plan")
(def ^:private ENROLLED_COLUMN_HEADER "Number of Individuals Who Have Selected a Marketplace Plan ")

(defn- fetch-workbook
  "Fetch xls workbook from catalog.data.gov/dataset and loads in memory"
  []
  (let [response (client/get WORKBOOK_URL {:as :byte-array :throw-exceptions false})]
    (with-open [xin (io/input-stream (:body response))]
      (doc/load-workbook xin))))

(defn- calculate-percentage
  [{:keys [enrolled total]}]
  (format "%.2f" (* 100 (float (/ enrolled total)))))

(defn- load-state-level-data
  "Selects columns of interest from workbook"
  [workbook]
  (->> workbook
       (doc/select-sheet "State Level Tables From Report")
       (doc/select-columns {:B :state :E :total :H :enrolled})
       (remove #(or (= "StateName" (:state %)) (nil? (:state %))))
       (map #(assoc % :enrolled-percentage (calculate-percentage %)))))

(defn- get-marketplace-data
  [_]
  (let [state-level-data (load-state-level-data (fetch-workbook))]
    {:status 200 :body state-level-data}))

(def app
  (http/ring-handler
    (http/router
      [["/about"
        {:get {:handler (fn [_] {:status 200 :body "ok"})}}]
       ["/marketplace"
        {:get {:handler get-marketplace-data}}]])
    (ring/routes
      (ring/create-default-handler))
    {:executor sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false, :async true})
  ;(aleph/start-server (aleph/wrap-ring-async-handler #'app) {:port 3000})
  (println "server running in port 3000"))
