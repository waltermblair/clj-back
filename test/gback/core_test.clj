(ns gback.core-test
  (:require [bond.james :as bond :refer [with-stub]]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [dk.ative.docjure.spreadsheet :as doc]
            [gback.core :as core]
            [gback.services.db :as db]
            [gback.services.healthcare :as health]
            [ring.mock.request :as mock]))

(deftest handle-get-marketplace-data-test
  (testing "Returns 200 with data"
    (with-redefs [health/fetch-workbook (constantly (doc/load-workbook "test/resources/workbook.xls"))]
      (let [response (core/handle-get-marketplace-data (mock/request :get "/api/1/marketplace"))
            body (-> response :body (json/read-str :key-fn keyword))]
        (is (= 200 (:status response)))
        (is (= "California" (-> body first :state)))
        (is (= 74 (-> body first :enrolled-percentage))))))
  (testing "Returns 500 on unhandled exception"
    (with-redefs [health/fetch-workbook (constantly (Exception. "oops"))]
      (let [response (core/handle-get-marketplace-data (mock/request :get "/api/1/marketplace"))]
        (is (= 500 (:status response)))
        (is (= "Unhandled Exception" (:body response)))))))

(deftest handle-post-test
  (let [request-body {:state "SC" :guessed-percent-enrolled 95 :actual-percent-enrolled 50}]
    (testing "Returns 201 with data"
      (with-stub [[db/post-guess (constantly {:average_difference 50})]]
        (let [response (core/handle-post-guess request-body)]
          (is (= 201 (:status response)))
          (is (= 50 (-> response :body (json/read-str :key-fn keyword) :average_difference))))
        (is (= 1 (-> db/post-guess bond/calls count)))))
    (testing "Returns 400 on bad request"
      (let [response (core/handle-post-guess (dissoc request-body :state))]
        (is (= 400 (:status response)))))
    (testing "Returns 500 on DB error"
      (with-redefs [db/post-guess (fn [_] (throw (Exception. "oops")))]
        (let [response (core/handle-post-guess request-body)]
          (is (= 500 (:status response))))))))