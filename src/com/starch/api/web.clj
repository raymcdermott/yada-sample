(ns com.starch.api.web
  (:require [yada.yada :as yada]
            [environ.core :refer [env]]
            [com.starch.message.pubsub :as ps])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap)))

(def version (UUID/randomUUID))

(def system-origin {:type "system"})

;(def transfer-post-data {:amount          1337
;                         :customer-source (UUID/randomUUID)
;                         :customer-target (UUID/randomUUID)})

(defn new-transfer-command
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
               :href (str ps/api-url "customers/" (:customer-source post-data))}
   })

(defn create-transfer-event [transfer-resource]
  {:id            (UUID/randomUUID)
   :relationships (:relationships transfer-resource)
   :time-stamp    (new Date)
   :version       version
   :origin        system-origin
   :resource      {:id   (:id transfer-resource),
                   :href (str ps/api-url "transfers/" (:id transfer-resource))}})

(defn publish-initiated-transfer-event
  [transfer-resource]
  (ps/publish-event (merge (create-transfer-event transfer-resource)
                           {:event "transferInitiated"}))
  transfer-resource)

(defn create-expired-transfer-event
  [ctx]
  (let [transfer-resource (get-in ctx [:response :body])]
    (ps/publish-event (merge (create-transfer-event transfer-resource)
                             {:event "transferClaimExpired"})))
  ctx)

(defn create-failed-transfer-event
  [ctx]
  (let [transfer-resource (get-in ctx [:response :body])]
    (ps/publish-event (merge (create-transfer-event transfer-resource)
                             {:event   "transferFailed"
                              :context {:reason "INSUFFICIENT_FUNDS"}})))
  ctx)

(defn process-command
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
    (let [resource (ps/resource-listener 200)]
      (println "process-command - return type" (type resource))

      (condp instance? resource

        ; it worked, async publish the initiated event
        PersistentArrayMap (publish-initiated-transfer-event resource)

        Exception (:cause resource)

        resource))))

(def transfer-parameters-resource
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