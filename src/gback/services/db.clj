(ns gback.services.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [environ.core :refer [env]]))

(def ds (jdbc/get-datasource (cond-> {:dbtype "mysql" :dbname "gback" :user "username" :password "password"}
                               (:db-host env)
                               (merge {:host (:db-host env)}))))

(def insert-guess-query
  "INSERT INTO Guesses(state, actual_percent_enrolled, guessed_percent_enrolled)
  VALUES (?,?,?)")

(defn insert-guess
  [conn {:keys [state actual-percent-enrolled guessed-percent-enrolled]}]
  (jdbc/execute-one! conn [insert-guess-query state actual-percent-enrolled guessed-percent-enrolled]))

(def guess-results-query
  "SELECT CAST(ROUND(AVG(ABS(actual_percent_enrolled - guessed_percent_enrolled)), 0) AS SIGNED) as Average_Difference
  FROM Guesses
  WHERE state = ?")

(defn fetch-guess-results
  [conn {:keys [state]}]
  (jdbc/execute-one! conn [guess-results-query state] {:builder-fn rs/as-unqualified-lower-maps}))

(defn post-guess
  [guess]
  (with-open [conn (jdbc/get-connection ds)]
    (insert-guess conn guess)
    (fetch-guess-results conn guess)))