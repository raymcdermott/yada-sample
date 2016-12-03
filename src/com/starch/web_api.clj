(ns com.starch.web-api
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.pubsub :as ps]
            [com.starch.data-storage :as ds])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

(def deadline-millis (Long. (or (env :deadline-millis) 200)))

(defn sync-command-event
  [command resource-locater]
  (let [command-keys [:origin :id]
        resource-keys [:resource :id]]
    (ps/sync-command-with-result command command-keys resource-keys resource-locater deadline-millis)))

(defn- fail-transfer-event-source
  [ctx]
  (let [form (get-in ctx [:parameters :query])
        transfer-id (UUID/fromString (:transfer-id form))
        post-data {:transfer-id transfer-id}
        command (ps/fail post-data)]
    (sync-command-event command ds/lookup-transfer)))

(defn- expire-transfer-event-source
  [ctx]
  (let [form (get-in ctx [:parameters :query])
        transfer-id (UUID/fromString (:transfer-id form))
        post-data {:transfer-id transfer-id}
        command (ps/expire post-data)]
    (sync-command-event command ds/lookup-transfer)))

(defn- initiate-transfer-event-source
  [ctx]
  (let [form (get-in ctx [:parameters :query])
        post-data {:amount          (:amount form)
                   :customer-source (:customer-source form)
                   :customer-target (:customer-target form)}
        command (ps/create post-data)]
    (sync-command-event command ds/lookup-transfer)))

(def ^:private fail-transfer-schema                       ; TODO integrate with spec
  {:transfer-id String})

(def ^:private fail-transfer
  (let [resource-method-map {:parameters {:query fail-transfer-schema}
                             :produces   "application/json" ; hmmm, who likes a hard coded MIME type? Can keyword??
                             :response   fail-transfer-event-source}]
    (yada/resource
      {:methods
       {:post resource-method-map
        :get  resource-method-map}})))

(def ^:private expire-transfer-schema                       ; TODO integrate with spec
  {:transfer-id String})

(def ^:private expire-transfer
  (let [resource-method-map {:parameters {:query expire-transfer-schema}
                             :produces   "application/json" ; hmmm, who likes a hard coded MIME type? Can keyword??
                             :response   expire-transfer-event-source}]
    (yada/resource
      {:methods
       {:post resource-method-map
        :get  resource-method-map}})))

(def ^:private initiate-transfer-schema                     ; TODO integrate with spec
  {:customer-source String
   :customer-target String
   :amount          Double})

(def ^:private initiate-transfer
  (let [resource-method-map {:parameters {:query initiate-transfer-schema}
                             :produces   "application/json" ; hmmm, who likes a hard coded MIME type? Can keyword??
                             :response   initiate-transfer-event-source}]
    (yada/resource
      {:methods
       {:post resource-method-map
        :get  resource-method-map}})))



; For running on servers

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (yada/listener
      ["/"
       [["hello" (yada/handler "Hello World!")]
        ["initiate" (yada/handler initiate-transfer)]
        ["expire" (yada/handler expire-transfer)]
        ["fail" (yada/handler fail-transfer)]]]
      {:port port})))


; For running in the repl ...
; (def srv (-main))
; start the server, obtain the map
;
; ((:close srv))
; shut it down