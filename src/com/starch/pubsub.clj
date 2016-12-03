(ns com.starch.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

(def ^:private api-domain "transfers-api.starch.com")

(def api-url (str "https://" api-domain "/"))

(def ^:private version (UUID/randomUUID))

; TODO - move transfer specific stuff out of here
; commands

(defn create-transfer-command
  [post-data]
  {
   :id        (UUID/randomUUID)
   :command   :create-transfer
   :timestamp (System/currentTimeMillis)
   :version   version
   :origin    {:type      :api
               :requestId (UUID/randomUUID)}                ; TODO - integrate X-Request-Id
   :context   post-data
   :resource  {:id   (:customer-source post-data)
               :href (str api-url "customers/" (:customer-source post-data))}})

(defn expire-transfer-command
  [post-data]
  {
   :id        (UUID/randomUUID)
   :command   :expire-transfer
   :timestamp (System/currentTimeMillis)
   :version   version
   :origin    {:type      :api
               :requestId (UUID/randomUUID)}                ; TODO - integrate X-Request-Id
   :context   post-data
   :resource  {:id   (:transfer-id post-data)
               :href (str api-url "transfers/" (:transfer-id post-data))}})

; events


; channels

(def ^:private events-ch (chan (buffer 256)))

(def ^:private commands-ch (chan (buffer 256)))

(def ^:private stop-commands-ch (chan))


; support processing commands

(def ^:private commands-mult (mult commands-ch))

(defn command-listener
  [transformer]
  (let [command-events-ch (chan (buffer 256) transformer)
        _ (tap commands-mult command-events-ch)]
    (go-loop []
      (when-let [[command-event ch] (alts!! [command-events-ch stop-commands-ch])]
        (condp = ch
          command-events-ch (do (>! events-ch command-event)
                                (recur))
          stop-commands-ch (untap commands-mult command-events-ch))))))

; for interactive use
(defn- stop-command-listener []
  (put! stop-commands-ch :stop))


; support finding the event as a result of command

(def ^:private events-mult (mult events-ch))

(defn command-event-listener
  [command-id predicate timeout-ms]
  (let [predicate-ch (chan 1 predicate)
        _ (tap events-mult predicate-ch)
        timeout-ch (timeout timeout-ms)]
    (when-let [[event ch] (alts!! [predicate-ch timeout-ch])]
      (untap events-mult predicate-ch)
      (condp = ch
        predicate-ch event
        timeout-ch (do (error "Timout in command-event-listener" {:command-id command-id :timeout timeout-ms})
                       (ex-info "Timout in command-event-listener" {:command-id command-id :timeout timeout-ms}))))))

; support publishing commands and events

(defn- publish [ch message]
  (put! ch message))

(def publish-event (partial publish events-ch))
(def publish-command (partial publish commands-ch))

; TODO Add Schema
(defn sync-command-with-result
  "General sync handler for events that create or update resources"
  [command command-keys resource-keys resource-locater timeout-ms]

  (info "Publishing command id:" (:id command) "with type" (:command command))

  ; publish the command
  (publish-command command)

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

      (str "sync-command-with-result FAIL - unmatched instance type: " (type event)))))

