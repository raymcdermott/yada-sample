(ns com.starch.api.web
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.message.pubsub :as ps]
            [com.starch.data.storage :as ds])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap)))

(def ^:private version (UUID/randomUUID))

(defn- new-transfer-command
  [post-data]
  {
   :id        (UUID/randomUUID)
   :command   "transfer"
   :timestamp (new Date)
   :version   version
   :origin    {:type      "api"
               :requestId (UUID/randomUUID)}
   :context   post-data
   :resource  {:id   (:customer-source post-data)
               :href (str ps/api-url "customers/" (:customer-source post-data))}})

; make more functional!!

(defn- process-command
  [ctx]
  (let [cust-src (get-in ctx [:parameters :query :src])
        cust-tgt (get-in ctx [:parameters :query :tgt])
        xfer-amt (get-in ctx [:parameters :query :amt])
        post-data {:amount          xfer-amt
                   :customer-source cust-src
                   :customer-target cust-tgt}
        command (new-transfer-command post-data)]

    ; publish the command
    (ps/publish-command command)

    ; wait for the command to complete / timeout
    (let [id (:id command)
          event (ps/command-event-listener
                  id (filter #(= id (get-in % [:origin :id]))) 200)]

      (condp instance? event

        ; it worked, async publish the initiated event
        PersistentArrayMap (when-let [transfer-id (get-in event [:resource :id])]
                             (ds/lookup-transfer transfer-id))

        ; fail, create a small map of the error
        Exception {:fail (:cause event) :posted-data post-data}

        "process-command - unmatched instance type"))))

(def ^:private transfer-parameters-resource
  (yada/resource
    {:methods
     {:get
      {:parameters {:query {:src String
                            :tgt String
                            :amt Double}}
       :produces   "application/json"
       :response   process-command}}}))


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
; (def srv (-main)) ; start the server, obtain the map
; ((:close srv))    ; shut it down