(ns com.starch.api.web
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.message.pubsub :as ps]
            [com.starch.data.storage :as ds])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

; make more functional / reduce boiler plate!!

(def default-timeout 200)


; publish on POST (standard lifecycle)

; obtain RESOURCE before 'create response'

(defn process-command
  [command resource-locater]

  ; publish the command
  (ps/publish-command command)

  ; wait for the command to complete / timeout
  (let [id (:id command)
        event (ps/command-event-listener
                id (filter #(= id (get-in % [:origin :id]))) default-timeout)]

    (condp instance? event

      ; it worked, async publish the initiated event
      PersistentArrayMap (when-let [resource-id (get-in event [:resource :id])]
                           (resource-locater resource-id))

      ; fail and provide a diagnostics map
      ExceptionInfo {:fail (:cause event) :posted-data (:context command) :exception event}

      "process-command - unmatched instance type")))

; can become a map? can be added to yada resource? yes ... and then just fix up the interceptor chain
; after process-request-body

(defn- process-transfer-command
  [post-data]
  (let [command (ps/new-transfer-command post-data)]
    (process-command command ds/lookup-transfer)))

(defn- transfer-event-source
  [ctx]
  (let [form (get-in ctx [:parameters :form])
        post-data {:amount          (:amount form)
                   :customer-source (:customer-source form)
                   :customer-target (:customer-target form)}]
    (process-transfer-command post-data)))

(def ^:private transfer-schema                              ; TODO add spec
  {:customer-source String
   :customer-target String
   :amount          Double})

(def ^:private transfer-parameters-resource
  (yada/resource
    {:methods
     {
      :post                                                 ; TODO
      {:parameters {:form transfer-schema}
       :produces   "application/json"
       :response   transfer-event-source}
      :get
      {:parameters {:query transfer-schema}
       :produces   "application/json"
       :response   transfer-event-source}}
     }))

; so, how to create the resource per request
(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (yada/listener
      ["/"
       [["hello" (yada/handler "Hello World!")]
        ["transfer" (yada/handler transfer-parameters-resource)]
        [true (yada/as-resource nil)]]]
      {:port port})))

; In the repl ... use this for interactive work
; (def srv (-main))
; start the server, obtain the map
; ((:close srv))
; shut it down