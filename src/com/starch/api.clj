(ns com.starch.api
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.listen :as listen]
            [com.starch.commands :as cmd]
            [com.starch.storage :as ds])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))


; Environment injected configurations
(defonce ^:private api-domain (or (env :api-domain) "transfers-api.starch.com"))
(defonce ^:private api-url (str "https://" api-domain "/"))

(defonce ^:private deadline-millis (Long. (or (env :deadline-millis) 1000)))


; Create the Yada event source command operation

(defn- sync-command
  [mapper publish-op ctx]
  (let [form (get-in ctx [:parameters :query])
        post-data (mapper form)
        command (publish-op post-data api-url)]
    (listen/sync-command-with-result command ds/lookup-transfer deadline-millis)))

(def ^:private fail-command
  (let [mapper (fn
                 [form]
                 (let [transfer-id (UUID/fromString (:transfer-id form))]
                   {:transfer-id transfer-id}))]
    (partial sync-command mapper cmd/fail)))

(def ^:private expire-command
  (let [mapper (fn
                 [form]
                 (let [transfer-id (UUID/fromString (:transfer-id form))]
                   {:transfer-id transfer-id}))]
    (partial sync-command mapper cmd/expire)))

(def ^:private initiate-command
  (let [mapper (fn
                 [form]
                 {:amount          (:amount form)
                  :customer-source (:customer-source form)
                  :customer-target (:customer-target form)})]
    (partial sync-command mapper cmd/create)))



; Create the Yada resource per route

(defn resource
  [schema event-source-fn]
  (let [handler {:parameters {:query schema}
                 :produces   "application/json"
                 :response   event-source-fn}]
    (yada/resource
      {:methods
       {:post handler
        :get  handler}})))

(def ^:private fail                                         ; TODO integrate with spec
  (let [schema {:transfer-id String}]
    (resource schema fail-command)))

(def ^:private expire                                       ; TODO integrate with spec
  (let [schema {:transfer-id String}]
    (resource schema expire-command)))

(def ^:private initiate                                     ; TODO integrate with spec
  (let [schema {:customer-source String
                :customer-target String
                :amount          Double}]
    (resource schema initiate-command)))



; For running on servers

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (yada/listener
      ["/"
       [["hello" (yada/handler "Hello World!")]
        ["initiate" (yada/handler initiate)]
        ["expire" (yada/handler expire)]
        ["fail" (yada/handler fail)]]]
      {:port port})))


; For running in the repl ...
; (def srv (-main))
; start the server, obtain the map
;
; ((:close srv))
; shut it down