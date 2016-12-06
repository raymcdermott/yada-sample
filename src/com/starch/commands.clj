(ns com.starch.commands
  (:import (java.util UUID)))

; TODO remove this hard coding and use the env
(defonce ^:private commit-hash (UUID/randomUUID))

(defn command
  [post-data name resource-id-key resource-path]
  (let [id (resource-id-key post-data)]
    {
     :id        (UUID/randomUUID)
     :command   name
     :timestamp (System/currentTimeMillis)
     :version   commit-hash
     :origin    {:type      :api
                 :requestId (UUID/randomUUID)}              ; TODO - integrate X-Request-Id
     :context   post-data
     :resource  {:id   id
                 :href (str resource-path id)}}))

(defn create
  [post-data api-url]
  (command post-data :create-transfer :customer-source (str api-url "customers/")))

(defn fail
  [post-data api-url]
  (command post-data :fail-transfer :transfer-id (str api-url "transfers/")))

(defn expire
  [post-data api-url]
  (command post-data :expire-transfer :transfer-id (str api-url "transfers/")))
