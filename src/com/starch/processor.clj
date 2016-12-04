(ns com.starch.processor
  (:require [com.starch.pubsub :as ps]
            [com.starch.storage :as store]
            [com.starch.data :as data]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (java.util UUID Date)
           (clojure.lang ExceptionInfo PersistentArrayMap)))



; TODO remove this hard coding and use the env
(def ^:private api-domain "transfers-api.starch.com")
(def ^:private api-url (str "https://" api-domain "/"))
(def ^:private commit-hash (UUID/randomUUID))

; event creators

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


(def ^:private stop-commands-ch (chan))

(defn command-listener
  [transformer]
  (let [command-events-ch (chan (buffer 256) transformer)
        _ (tap ps/commands-mult command-events-ch)]
    (go-loop []
             (when-let [[command-event ch] (alts!! [command-events-ch stop-commands-ch])]
               (condp = ch
                 command-events-ch (do (>! ps/commands-ch command-event)
                                       (recur))
                 stop-commands-ch (untap ps/commands-mult command-events-ch))))))

; for interactive use
(defn- stop-command-listener []
  (put! stop-commands-ch :stop))

(defn command-event-listener
  [command-id predicate timeout-ms]
  (let [predicate-ch (chan 1 predicate)
        _ (tap ps/events-mult predicate-ch)
        timeout-ch (timeout timeout-ms)]
    (when-let [[event ch] (alts!! [predicate-ch timeout-ch])]
      (untap ps/events-mult predicate-ch)
      (condp = ch
        predicate-ch event
        timeout-ch (do (error "Timout in command-event-listener" {:command-id command-id :timeout timeout-ms})
                       (ex-info "Timout in command-event-listener" {:command-id command-id :timeout timeout-ms}))))))

; TODO Add Schema
(defn sync-command-with-result
  "General sync handler for events that create or update resources"
  [command resource-locater timeout-ms]

  (let [command-keys [:origin :id]
        resource-keys [:resource :id]]

    (info "Publishing command id:" (:id command) "with type" (:command command))

    ; publish the command
    (put! ps/commands-ch command)

    ; wait for the command to complete / timeout
    (let [id (:id command)
          event (command-event-listener
                  id (filter #(= id (get-in % command-keys))) timeout-ms)]

      (condp instance? event

        ; it worked, async publish the resource tagged in the event
        PersistentArrayMap (do (info "The event produced in reaction to command id:" (:id command) "is" (:event event))
                               (when-let [resource-id (get-in event resource-keys)]
                                 (resource-locater resource-id)))

        ; fail and provide a diagnostics map
        ExceptionInfo {:fail (:cause event) :posted-data (:context command) :exception event}

        (str "sync-command-with-result FAIL - unmatched instance type: " (type event))))))



; Implement the pattern:
; - process the command on a resource (CRUD)
; - obtain the processed resource
; - publish the associated event
(defn- command-processor
  [command command-processor event]
  (if-let [resource (command-processor command)]
    (put! ps/events-ch (create-event resource event))
    (ex-info "Failed to process command" {:command command})))

; First draft of the map; might get larger as we flesh out design
(def command->function->event
  {:create-transfer {:fn    data/create-transfer
                     :event :transferInitiated}
   :expire-transfer {:fn    data/expire-transfer
                     :event :transferClaimExpired}
   :fail-transfer   {:fn    data/fail-transfer
                     :event :transferFailed}})

(defn command-channel-processor
  "Dispatch the appropriate processor for the command on the channel"
  [command-processor-map]
  (command-listener (map (fn [command]
                           (if-let [map-entry (get command-processor-map (:command command))]
                             (command-processor command (:fn map-entry) (:event map-entry))
                             (ex-info "Cannot map command to a function" {:command command :map command-processor-map}))))))





