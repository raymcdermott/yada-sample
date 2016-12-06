(ns com.starch.listen
  (:require [com.starch.pubsub :as ps]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (clojure.lang PersistentArrayMap ExceptionInfo)))

(defonce ^:private stop-commands-ch (chan))

(defn command-listener
  [transformer]
  (let [command-events-ch (chan (buffer 256) transformer)
        _ (tap ps/commands-mult command-events-ch)]
    (go-loop []
      (when-let [[command-event ch] (alts!! [command-events-ch stop-commands-ch])]
        (condp = ch
          command-events-ch (do (>! ps/events-ch command-event)
                                (recur))
          stop-commands-ch (untap ps/commands-mult command-events-ch))))))

; for interactive use
(defn- stop-command-listener []
  (put! stop-commands-ch :stop))


; support finding the event as a result of command
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
    (ps/publish-command command)

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

