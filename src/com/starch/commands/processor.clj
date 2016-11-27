(ns com.starch.api.commands.processor
  (:require [environ.core :refer [env]]
            [com.starch.message.pubsub :as ps]
            [clojure.core.async :refer [<! >! alts!! buffer chan
                                        close! go go-loop mult put!
                                        take! timeout tap untap]])
  (:import (java.util UUID)))

(defn- create-transfer-resource
  [transfer-command]
  (let [amount (get-in transfer-command [:context :amount])
        source (get-in transfer-command [:context :customer-source])
        target (get-in transfer-command [:context :customer-target])]
    {:id            (UUID/randomUUID)
     :type          "transfers"
     :attributes    {:amount amount}
     :relationships {:customer-source {:data  {:type "customers"
                                               :id   source}
                                       :links {:related (str ps/api-url "customers/" source)}
                                       }
                     :customer-target {:data  {:type "customers"
                                               :id   target}
                                       :links {:related (str ps/api-url "customers/" target)}
                                       }
                     }
     :origin        {
                     :type :command
                     :id   (:id transfer-command)
                     :href (str ps/api-url "commands/" (:id transfer-command))
                     }
     }))

(defn command-processor
  []
  (ps/command-listener (map #(create-transfer-resource %))))





