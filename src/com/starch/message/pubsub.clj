(ns com.starch.message.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

(def deadline-millis (Long. (or (env :deadline-millis) 200)))

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
   :timestamp (new Date)
   :version   version
   :origin    {:type      :api
               :requestId (UUID/randomUUID)}
   :context   post-data
   :resource  {:id   (:customer-source post-data)
               :href (str api-url "customers/" (:customer-source post-data))}})

; events


; channels

(def ^:private events-ch (chan (buffer 256)))

(def ^:private commands-ch (chan (buffer 256)))

(def ^:private stop-processing-ch (chan))


; support processing commands

(def ^:private commands-mult (mult commands-ch))

(defn command-listener
  [transformer]
  (let [command-events-ch (chan (buffer 256) transformer)
        _ (tap commands-mult command-events-ch)]
    (go-loop []
      (when-let [[command-event ch] (alts!! [command-events-ch stop-processing-ch])]
        (condp = ch
          command-events-ch (do (>! events-ch command-event)
                                (recur))
          stop-processing-ch (if (= :stop command-event)
                               (do (untap commands-mult command-events-ch)
                                   (println "Stopping command-listener"))
                               (recur)))))))


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
        timeout-ch (ex-info "Timout in command-event-listener" {:command-id command-id :timeout timeout-ms})))))

(defn stop-command-processor []
  (put! stop-processing-ch :stop))

; support publishing commands and events

(defn- publish [ch message]
  (put! ch message))

(def publish-event (partial publish events-ch))
(def publish-command (partial publish commands-ch))

; general sync command handler
(defn await-command-result
  [command resource-locater timeout-ms]

  ; publish the command
  (publish-command command)

  ; wait for the command to complete / timeout
  (let [id (:id command)
        event (command-event-listener
                id (filter #(= id (get-in % [:origin :id]))) timeout-ms)]

    (condp instance? event

      ; it worked, async publish the initiated event
      PersistentArrayMap (when-let [resource-id (get-in event [:resource :id])]
                           (resource-locater resource-id))

      ; fail and provide a diagnostics map
      ExceptionInfo {:fail (:cause event) :posted-data (:context command) :exception event}

      "process-command - unmatched instance type")))

