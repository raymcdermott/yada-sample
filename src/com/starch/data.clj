(ns com.starch.data
  (:require [com.starch.storage :as ds])
  (:import (java.util UUID)))


; TODO remove this hard coding and use the env
(defonce ^:private commit-hash (UUID/randomUUID))


; TODO remove this hard coding and use the env
(defonce ^:private api-domain "transfers-api.starch.com")
(defonce ^:private api-url (str "https://" api-domain "/"))


; create a transfer resource, store it
(defn create-transfer
  [command]
  (let [command-id (:id command)
        amount (get-in command [:context :amount])
        source (get-in command [:context :customer-source])
        target (get-in command [:context :customer-target])
        transfer-resource {:id            (UUID/randomUUID)
                           :time-stamp    (System/currentTimeMillis)
                           :version       commit-hash
                           :type          :transfer
                           :attributes    {:amount amount}
                           :relationships {:customer-source {:data  {:type :customer
                                                                     :id   source}
                                                             :links {:related (str api-url "customers/" source)} ; hmmm, who likes a hard coded path??
                                                             }
                                           :customer-target {:data  {:type :customer
                                                                     :id   target}
                                                             :links {:related (str api-url "customers/" target)} ; hmmm, who likes a hard coded path??
                                                             }}
                           :origin        {:type :command
                                           :id   command-id
                                           :href (str api-url "commands/" command-id)}}] ; hmmm, who likes a hard coded path??
    (ds/store-transfer! transfer-resource)))


(defn- update-transfer
  [command update-keys update-val]
  (let [transfer-id (get-in command [:context :transfer-id])]
    (if-let [transfer (ds/lookup-transfer transfer-id)]
      (let [command-id (:id command)
            updated-attributes (assoc-in transfer update-keys update-val)
            updated-meta-data {:time-stamp (System/currentTimeMillis)
                               :version    commit-hash
                               :origin     {:type :command
                                            :id   command-id
                                            :href (str api-url "commands/" command-id)}}] ; hmmm, who likes a hard coded path??
        (ds/store-transfer! (merge updated-attributes updated-meta-data)))
      (ex-info "Cannot find transfer to update" {:command command}))))

(defn expire-transfer
  [command]
  (update-transfer command [:attributes :expired] true))

(defn fail-transfer
  [command]
  (update-transfer command [:attributes :failed] true))

