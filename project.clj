(defproject yada-sample "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [yada "1.1.45" :exclusions [aleph manifold ring-swagger prismatic/schema]]
                 [aleph "0.4.2-alpha8"]
                 [manifold "0.1.6-alpha1"]
                 [gloss "0.2.6"]
                 [metosin/ring-swagger "0.22.10"]
                 [environ "1.1.0"]
                 [com.datomic/datomic-free "0.9.5544"]
                 [com.taoensso/timbre "4.7.4"]]
  :main ^:skip-aot com.starch.api.web
  :target-path "target/%s"
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:uberjar {:aot :all}
             :test    {:dependencies [[http.async.client "1.2.0"]
                                      [org.slf4j/slf4j-nop "1.7.21"]
                                      [cheshire "5.6.3"]]
                       :env          {:commit-hash "6770d2ee-e755-4546-a759-b52a5fab1ad6"}}}
  :pedantic? :warn)
