(ns gback.main
  (:require [gback.core :as core])
  (:gen-class))

(defn -main
  [& args]
  (core/start))