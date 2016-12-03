(ns com.starch.web-api
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.pubsub :as ps]
            [com.starch.data-storage :as ds])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

(def ^:private deadline-millis (Long. (or (env :deadline-millis) 1000)))


; Create the Yada event source command operation

(defn- transfer-op
  [post-op publish-op ctx]
  (let [form (get-in ctx [:parameters :query])
        post-data (post-op form)
        command (publish-op post-data)]
    (ps/sync-command-with-result command ds/lookup-transfer deadline-millis)))

(def ^:private fail-transfer
  (let [post-op (fn [form]
                  (let [transfer-id (UUID/fromString (:transfer-id form))]
                    {:transfer-id transfer-id}))]
    (partial transfer-op post-op ps/fail)))

(def ^:private expire-transfer
  (let [post-op (fn [form]
                  (let [transfer-id (UUID/fromString (:transfer-id form))]
                    {:transfer-id transfer-id}))]
    (partial transfer-op post-op ps/expire)))

(def ^:private initiate-transfer
  (let [post-op (fn [form] {:amount          (:amount form)
                            :customer-source (:customer-source form)
                            :customer-target (:customer-target form)})]
    (partial transfer-op post-op ps/create)))


; Create the Yada resource per route

(defn route-handler
  [parameter-schema event-source-fn]
  (let [resource-method-map {:parameters {:query parameter-schema}
                             :produces   "application/json" ; hmmm, who likes a hard coded MIME type? Can keyword??
                             :response   event-source-fn}]
    (yada/resource
      {:methods
       {:post resource-method-map
        :get  resource-method-map}})))

(def ^:private fail                                         ; TODO integrate with spec
  (let [schema {:transfer-id String}]
    (route-handler schema fail-transfer)))

(def ^:private expire                                       ; TODO integrate with spec
  (let [schema {:transfer-id String}]
    (route-handler schema expire-transfer)))

(def ^:private initiate                                     ; TODO integrate with spec
  (let [schema {:customer-source String
                :customer-target String
                :amount          Double}]
    (route-handler schema initiate-transfer)))


; For running on servers

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (yada/listener
      ["/"
       [["initiate" (yada/handler initiate)]
        ["expire" (yada/handler expire)]
        ["fail" (yada/handler fail)]]]
      {:port port})))


; For running in the repl ...
; (def srv (-main))
; start the server, obtain the map
;
; ((:close srv))
; shut it down