(ns gback.services.healthcare
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as doc]))

(def ^:private WORKBOOK_URL "https://aspe.hhs.gov/sites/default/files/private/aspe-files/106941/workbook.xls")

(defn select-data
  [workbook]
  (->> workbook
       (doc/select-sheet "State Level Tables From Report")
       (doc/select-columns {:B :state :E :eligible :H :enrolled})))

(defn fetch-workbook
  "Fetch xls workbook from catalog.data.gov/dataset and loads in memory"
  []
  (let [response (client/get WORKBOOK_URL {:as :byte-array :throw-exceptions false})]
    (with-open [xin (io/input-stream (:body response))]
      (doc/load-workbook xin))))