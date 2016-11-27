(ns com.starch.message.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [<! >! alts!! buffer chan close!
                                        go go-loop mult promise-chan
                                        put! take! timeout tap untap]]))

(def api-domain "transfers-api.starch.com")

(def api-url (str "https://" api-domain "/"))

(def commands-ch (chan (buffer 256)))

(def resource-ch (chan (buffer 256)))

(def events-ch (chan (buffer 256)))

(defn- publish [ch message]
  (put! ch message))

(def publish-command
  (partial publish commands-ch))

(def publish-event
  (partial publish events-ch))

(defn resource-listener
  [timeout-ms]
  (let [tc (timeout timeout-ms)]
    (when-let [[resource ch] (alts!! [resource-ch tc])]
      (condp = ch
        resource-ch resource
        tc (Exception. (str "Timed out for resource after " timeout-ms "ms"))))))

(defn command-listener
  [xform]
  (let [xform-ch (chan (buffer 256) xform)
        mc (mult commands-ch)
        listener (tap mc xform-ch)]
    (go-loop []
      (when-let [xformed (<! listener)]
        (when (>! resource-ch xformed)
          (println "published " (:origin xformed))))
      (recur))))

(defn event-listener
  [xform out-ch]
  (let [data-ch (chan 1 xform)
        mc (mult events-ch)
        listener (tap mc data-ch)]
    (go-loop []
      (when-let [event (<! listener)]
        (>! out-ch event)
        (recur)))))
