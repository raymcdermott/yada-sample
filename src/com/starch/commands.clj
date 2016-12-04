(ns com.starch.commands
  (:import (java.util UUID)))

; TODO integrate the environment
(def ^:private commit-hash (UUID/randomUUID))

(defn- command
  [name post-data resource-id-key resource-path]
  (let [id (resource-id-key post-data)]
    {:id        (UUID/randomUUID)
     :command   name
     :timestamp (System/currentTimeMillis)
     :version   commit-hash
     :origin    {:type      :api
                 :requestId (UUID/randomUUID)}              ; TODO - integrate X-Request-Id
     :context   post-data
     :resource  {:id   (resource-id-key post-data)
                 :href (str resource-path (resource-id-key post-data))}}))

(defn create
  [post-data api-url]
  (command :create-transfer post-data :customer-source (str api-url "customers/"))) ; TODO - why not transfer as the resource; this doesn't look right

(defn fail
  [post-data api-url]
  (command :fail-transfer post-data :transfer-id (str api-url "transfers/")))

(defn expire
  [post-data api-url]
  (command :expire-transfer post-data :transfer-id (str api-url "transfers/")))

