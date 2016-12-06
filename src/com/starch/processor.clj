(ns com.starch.processor
  (:require [com.starch.pubsub :as ps]
            [com.starch.listen :as listen]
            [com.starch.storage :as ds]
            [com.starch.data :as data]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (java.util UUID Date)))

; TODO remove this hard coding and use the env
(defonce ^:private api-domain "transfers-api.starch.com")
(defonce ^:private api-url (str "https://" api-domain "/"))
(defonce ^:private commit-hash (UUID/randomUUID))


(defn- create-event
  [resource event]
  {:id            (UUID/randomUUID)
   :time-stamp    (System/currentTimeMillis)
   :version       commit-hash
   :event         event
   :relationships (:relationships resource)
   :origin        (:origin resource)                        ; not as our spec - but more useful / correct [I think]
   :resource      {:id   (:id resource),
                   :href (str api-url "transfers/" (:id resource))}}) ; hmmm, who likes a hard coded path??


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
(defonce command->function->event
         {:create-transfer {:fn    data/create-transfer
                            :event :transferInitiated}
          :expire-transfer {:fn    data/expire-transfer
                            :event :transferClaimExpired}
          :fail-transfer   {:fn    data/fail-transfer
                            :event :transferFailed}})

(defn command-channel-processor
  "Dispatch the appropriate processor for the command on the channel"
  [command-processor-map]
  (listen/command-listener (map (fn [command]
                                  (if-let [map-entry (get command-processor-map (:command command))]
                                    (command-processor command (:fn map-entry) (:event map-entry))
                                    (ex-info "Cannot map command to a function" {:command command :map command-processor-map}))))))





