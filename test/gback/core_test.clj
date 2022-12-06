(ns gback.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [dk.ative.docjure.spreadsheet :as doc]
            [gback.core :as core]
            [ring.mock.request :as mock]))

(deftest get-marketplace-data-test
  (testing "Returns 200 with data"
    (with-redefs [core/fetch-workbook (constantly (doc/load-workbook "test/resources/workbook.xls"))]
      (let [response (core/get-marketplace-data (mock/request :get "/api/1/marketplace"))]
        (is (= 200 (:status response)))
        (is (= "Marketplace Total" (-> response :body first :state)))
        (is (= "59.20" (-> response :body first :enrolled-percentage))))))
  (testing "Returns 500 on unhandled exception"
    (with-redefs [core/fetch-workbook (constantly (Exception. "oops"))]
      (let [response (core/get-marketplace-data (mock/request :get "/api/1/marketplace"))]
        (is (= 500 (:status response)))
        (is (= "Unhandled Exception" (:body response)))))))
