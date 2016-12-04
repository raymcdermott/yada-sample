(ns com.starch.pubsub
  (:require [environ.core :refer [env]]
            [clojure.core.async :refer [>! buffer chan mult put!]]
            [taoensso.timbre :as timbre :refer [debug info warn error fatal]])
  (:import (java.util UUID Date)
           (clojure.lang PersistentArrayMap ExceptionInfo)))

; TODO connect up to Kafka

; channels

(def events-ch (chan (buffer 256)))

(def commands-ch (chan (buffer 256)))

; only have one each of these; multiple causes issues
(def commands-mult (mult commands-ch))
(def events-mult (mult events-ch))

