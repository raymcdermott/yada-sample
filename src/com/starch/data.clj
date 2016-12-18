(ns com.starch.data
  (:require [environ.core :refer [env]]
            [com.starch.storage :as ds])
  (:import (java.util UUID Date)))

; Environment injected configurations
(defonce ^:private commit-hash (or (env :commit-hash)
                                   (throw (Exception. "Cannot start without knowing the code version"))))

(defonce ^:private api-domain (or (env :api-domain) "transfers-api.starch.com"))
(defonce ^:private api-url (str "https://" api-domain "/"))

(defn transfer-resource
  "Create a JSON style resource"
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

(defn create-transfer
  [command]
  (let [conn (ds/get-conn)
        command-id (:id command)
        amount (get-in command [:context :amount])
        source (get-in command [:context :customer-source])
        target (get-in command [:context :customer-target])
        transfer-data {:id              (UUID/randomUUID)
                       :time-stamp      (new Date)
                       :version         commit-hash
                       :amount          amount
                       :customer-source source
                       :customer-target target
                       :command-id      command-id}]
    (when (ds/store-transfer! transfer-data)
      (transfer-resource transfer-data))))

(defn- update-transfer
  [command new-data]
  (let [transfer (ds/lookup-transfer (get-in command [:context :transfer-id]))]
    (if-not transfer
      (ex-info "Cannot find transfer to update" {:command command})
      (let [command-id (:id command)
            updated-transfer (merge transfer new-data
                                    {:command-id command-id
                                     :time-stamp (new Date)
                                     :version    commit-hash})]
        (when (ds/store-transfer! updated-transfer)
          (transfer-resource updated-transfer))))))

(defn expire-transfer
  [command]
  (update-transfer command {:expired true}))

(defn fail-transfer
  [command]
  (update-transfer command {:failed true}))

