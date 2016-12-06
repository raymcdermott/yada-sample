(ns com.starch.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [buffer chan mult put!]])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

; channels

(defonce events-ch (chan (buffer 256)))
(defonce commands-ch (chan (buffer 256)))

; multiples - having > one of these per channel gave me issues
(defonce events-mult (mult events-ch))
(defonce commands-mult (mult commands-ch))

; support publishing commands and events

(defn- publish [ch message]
  (put! ch message))

(defonce publish-event (partial publish events-ch))
(defonce publish-command (partial publish commands-ch))
