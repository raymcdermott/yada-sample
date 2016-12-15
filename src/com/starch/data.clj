(ns com.starch.data
  (:require [environ.core :refer [env]]
            [com.starch.storage :as ds])
  (:import (java.util UUID)))

; Environment injected configurations
(defonce ^:private commit-hash (or (env :commit-hash)
                                   (throw (Exception. "Cannot start without knowing the code version"))))

(defonce ^:private api-domain (or (env :api-domain) "transfers-api.starch.com"))
(defonce ^:private api-url (str "https://" api-domain "/"))

(defn transfer-resource
  [transfer-data]
  {:id            (:id transfer-data)
   :time-stamp    (:time-stamp transfer-data)
   :version       (:version transfer-data)
   :type          :transfer
   :attributes    {:amount (:amount transfer-data)}
   :relationships {:customer-source {:data  {:type :customer
                                             :id   (:customer-source transfer-data)}
                                     :links {:related (str api-url "customers/" (:customer-source transfer-data))}} ; hmmm, who likes a hard coded path??
                   :customer-target {:data  {:type :customer
                                             :id   (:customer-target transfer-data)}
                                     :links {:related (str api-url "customers/" (:customer-target transfer-data))}}} ; hmmm, who likes a hard coded path??
   :origin        {:type :command
                   :id   (:command-id transfer-data)
                   :href (str api-url "commands/" (:command-id transfer-data))}})

; create a transfer resource, store it
(defn create-transfer
  [command]
  (let [command-id (:id command)
        amount (get-in command [:context :amount])
        source (get-in command [:context :customer-source])
        target (get-in command [:context :customer-target])
        transfer-data {:id              (UUID/randomUUID)
                       :time-stamp      (System/currentTimeMillis)
                       :version         commit-hash
                       :amount          amount
                       :customer-source source
                       :customer-target target
                       :command-id      command-id}]        ; TODO fix schema to include these properties
    (ds/store-transfer! (transfer-resource transfer-data))))


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

