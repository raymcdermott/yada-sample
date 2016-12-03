(ns com.starch.commands-processor
  (:require [com.starch.pubsub :as ps]
            [com.starch.data-storage :as ds]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (java.util UUID Date)))

(def ^:private version (UUID/randomUUID))

; event creators

(defn- create-event
  [resource event]
  {:id            (UUID/randomUUID)
   :time-stamp    (System/currentTimeMillis)
   :version       version
   :event         event
   :relationships (:relationships resource)
   :origin        (:origin resource)               ; not as our spec - but more useful / correct [I think]
   :resource      {:id   (:id resource),
                   :href (str ps/api-url "transfers/" (:id resource))}}) ; hmmm, who likes a hard coded path??

; create a transfer resource, store it
(defn- create-transfer
  [command]
  (let [command-id (:id command)
        amount (get-in command [:context :amount])
        source (get-in command [:context :customer-source])
        target (get-in command [:context :customer-target])
        transfer-resource {:id            (UUID/randomUUID)
                           :time-stamp    (System/currentTimeMillis)
                           :version       version
                           :type          :transfer
                           :attributes    {:amount amount}
                           :relationships {:customer-source {:data  {:type :customer
                                                                     :id   source}
                                                             :links {:related (str ps/api-url "customers/" source)} ; hmmm, who likes a hard coded path??
                                                             }
                                           :customer-target {:data  {:type :customer
                                                                     :id   target}
                                                             :links {:related (str ps/api-url "customers/" target)} ; hmmm, who likes a hard coded path??
                                                             }}
                           :origin        {:type :command
                                           :id   command-id
                                           :href (str ps/api-url "commands/" command-id)}}] ; hmmm, who likes a hard coded path??
    (ds/store-transfer! transfer-resource)))


(defn- update-transfer
  [command update-keys update-val]
  (let [transfer-id (get-in command [:context :transfer-id])]
    (if-let [transfer (ds/lookup-transfer transfer-id)]
      (let [command-id (:id command)
            updated-attributes (assoc-in transfer update-keys update-val)
            updated-meta-data {:time-stamp (System/currentTimeMillis)
                               :version    version
                               :origin     {:type :command
                                            :id   command-id
                                            :href (str ps/api-url "commands/" command-id)}}] ; hmmm, who likes a hard coded path??
        (ds/store-transfer! (merge updated-attributes updated-meta-data)))
      (error "Cannot find transfer to update" {:command command}))))

(defn- expire-transfer
  [command]
  (update-transfer command [:attributes :expired] true))

(defn- fail-transfer
  [command]
  (update-transfer command [:attributes :failed] true))


; Implement the pattern:
; - process the command on a resource (CRUD)
; - obtain the processed resource
; - publish the associated event
(defn- command-processor
  [command command-processor event]
  (if-let [resource (command-processor command)]
    (ps/publish-event (create-event resource event))
    (ex-info "Failed to process command" {:command command})))

; First draft of the map; might get larger as we flesh out design
(def command->function->event
  {:create-transfer {:fn    create-transfer
                     :event :transferInitiated}
   :expire-transfer {:fn    expire-transfer
                     :event :transferClaimExpired}
   :fail-transfer   {:fn    fail-transfer
                     :event :transferFailed}})

(defn command-channel-processor
  "Dispatch the appropriate processor for the command on the channel"
  [command-processor-map]
  (ps/command-listener (map (fn [command]
                              (if-let [map-entry (get command-processor-map (:command command))]
                                (command-processor command (:fn map-entry) (:event map-entry))
                                (ex-info "Cannot map command to a function" {:command command :map command-processor-map}))))))





