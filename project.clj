(defproject gback "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :main ^:skip-aot gback.main
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.12.3"]
                 [com.github.seancorfield/next.jdbc "1.3.847"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [dk.ative/docjure "1.17.0"]
                 [metosin/reitit "0.5.18"]
                 [mysql/mysql-connector-java "8.0.31"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring-cors "0.1.13"]]
  :profiles {:dev {:plugins [[lein-auto "0.1.3"]]}
             :test {:dependencies [[ring/ring-mock "0.4.0"]]}}
  :repl-options {:init-ns gback.core})
