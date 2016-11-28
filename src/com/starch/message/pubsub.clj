(ns com.starch.message.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]]))

(def api-domain "transfers-api.starch.com")

(def api-url (str "https://" api-domain "/"))

(def ^:private events-ch (chan (buffer 256)))

(def ^:private commands-ch (chan (buffer 256)))

(defn- publish [ch message]
  (put! ch message))

(def publish-event
  (partial publish events-ch))

(def publish-command
  (partial publish commands-ch))


; support processing commands

(def ^:private commands-mult (mult commands-ch))

(defn command-listener
  [transformer]
  (let [command-events-ch (chan (buffer 256) transformer)
        _ (tap commands-mult command-events-ch)]
    (go-loop []
      (when-let [command-event (<! command-events-ch)]
        (>! events-ch command-event))
      (recur))))


; support finding the event as a result of command

(def ^:private events-mult (mult events-ch))

(defn command-event-listener
  [command-id predicate timeout-ms]
  (let [predicate-ch (chan 1 predicate)
        _ (tap events-mult predicate-ch)
        tc (timeout timeout-ms)]
    (when-let [[event ch] (alts!! [predicate-ch tc])]
      (untap events-mult predicate-ch)
      (condp = ch
        predicate-ch event
        tc (Exception. (str "Timed out for event from command id " command-id " after " timeout-ms "ms"))))))


