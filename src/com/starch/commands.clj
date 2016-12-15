(ns com.starch.commands
  (:require [environ.core :refer [env]])
  (:import (java.util UUID)))

; Environment injected configurations
(defonce ^:private commit-hash (or (env :commit-hash)
                                   (throw (Exception. "Cannot start without knowing the code version"))))

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
