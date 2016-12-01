(ns com.starch.commands-processor
  (:require [com.starch.pubsub :as ps]
            [com.starch.data-storage :as ds])
  (:import (java.util UUID Date)))

(def ^:private system-origin {:type "system"})

(def ^:private version (UUID/randomUUID))

(defn- create-transfer-event [transfer-resource]
  {:id            (UUID/randomUUID)
   :relationships (:relationships transfer-resource)
   :time-stamp    (new Date)
   :version       version
   :origin        system-origin
   :resource      {:id   (:id transfer-resource),
                   :href (str ps/api-url "transfers/" (:id transfer-resource))}})

(defn- create-initiated-transfer-event
  [transfer-resource]
  (merge (create-transfer-event transfer-resource)
         {:event  "transferInitiated"
          :origin (:origin transfer-resource)}))            ; not as spec - but correct I think

(defn- create-expired-transfer-event
  [ctx]
  (let [transfer-resource (get-in ctx [:response :body])]
    (ps/publish-event (merge (create-transfer-event transfer-resource)
                             {:event "transferClaimExpired"})))
  ctx)

(defn- create-failed-transfer-event
  [ctx]
  (let [transfer-resource (get-in ctx [:response :body])]
    (ps/publish-event (merge (create-transfer-event transfer-resource)
                             {:event   "transferFailed"
                              :context {:reason "INSUFFICIENT_FUNDS"}})))
  ctx)

; create a transfer resource, store it and return an initiated event

(defn- process-create-transfer-command
  [transfer-command]
  (let [amount (get-in transfer-command [:context :amount])
        source (get-in transfer-command [:context :customer-source])
        target (get-in transfer-command [:context :customer-target])
        transfer-resource {:id            (UUID/randomUUID)
                           :type          "transfers"
                           :attributes    {:amount amount}
                           :relationships {:customer-source {:data  {:type "customers"
                                                                     :id   source}
                                                             :links {:related (str ps/api-url "customers/" source)}
                                                             }
                                           :customer-target {:data  {:type "customers"
                                                                     :id   target}
                                                             :links {:related (str ps/api-url "customers/" target)}
                                                             }
                                           }
                           :origin        {:type :command
                                           :id   (:id transfer-command)
                                           :href (str ps/api-url "commands/" (:id transfer-command))
                                           }
                           }

        _ (ds/store-transfer transfer-resource)]

    transfer-resource))


(defn- handle-create-transfer
  [command]
  (if-let [transfer-resource (process-create-transfer-command command)]
    (create-initiated-transfer-event transfer-resource)
    (ex-info "Cannot handle create-transfer command" {:command command})))


; install a stop channel to enable more interactive control

(def command->function
  {:create-transfer handle-create-transfer})

(defn transfer-command-processor
  "Dispatch the appropriate processor for the command on the channel"
  [function-mapping]
  (ps/command-listener (map (fn [command]
                              (if-let [handler (get command->function (:command command))]
                                (handler command)
                                (ex-info "Cannot map command to a handler" {:command command :map function-mapping}))))))




