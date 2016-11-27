(defproject yada-sample "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [yada "1.1.43"]
                 [aleph "0.4.1"]
                 [bidi "2.0.9"]
                 [environ "1.1.0"]]
  :main ^:skip-aot com.starch.api.web
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :pedantic? :warn)
