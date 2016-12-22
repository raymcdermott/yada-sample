(ns com.starch.storage
  (:require [environ.core :refer [env]]
            [datomic.api :as d]
            [clojure.java.io :as io]))

(defn get-conn
  [uri]
  (d/connect uri))

;----------> Code

; TODO take this out of here once we get beyond transfers

; TODO spec the shape of this data - second arg of vector is tricky as must be dynamic
; TODO specifically, integrate a lookup in Datomic for the possible values of each entity
; TODO it can take a function that produces such a value or a value of that type

(def transfer-mapping
  "Datomic schema name and either a keyword or a two value list (key and some other thing)"
  {:db/id                    [:db/id (d/tempid :db.part/user)]
   :transfer/id              :id
   :transfer/time-stamp      :time-stamp
   :transfer/source-customer :source-customer
   :transfer/target-customer :target-customer
   :transfer/amount          :amount
   :transfer/expired         [:expired false]
   :transfer/failed          [:failed false]
   :code/version             :version
   :command/id               :command-id})

(defn m->d [mapping src]
  "Associate data from a source map into a Datomic schema using the provided mapping"
  (into {} (map #(let [val (% mapping)]
                   (if (keyword? val)
                     [% (val src)]
                     [% (or ((first val) src) (second val))]))
                (keys mapping))))

(defn d->m [mapping result]
  "Associate a single Datomic query result into a map using the provided mapping"
  (into {} (map #(let [val (% mapping)]
                   (if (keyword? val)
                     [val (% result)]
                     [(first val) (% result)]))
                (keys mapping))))

(defn store-in-datomic!
  "Add the data and return the transaction result"
  [conn transfer]
  (let [txn-data (vec (m->d transfer-mapping transfer))]
    (when @(d/transact conn txn-data)
      transfer)))

(defn lookup-in-datomic
  [conn transfer-id]
  (let [db (d/db conn)]
    (when-let [entity (d/entity db [:transfer/id transfer-id])]
      (d->m transfer-mapping (d/pull db '[*] (:db/id entity))))))

(defn update-in-datomic!
  [conn transfer]
  (when-let [found (lookup-in-datomic conn (:id transfer))]
    (let [merged (merge found transfer)]
      (store-in-datomic! conn merged))))

(def ^:private resource-db (atom #{}))

(defn store-transfer!
  [transfer]
  (swap! resource-db conj transfer)
  transfer)

(defn lookup-transfer
  [transfer-id]
  (when-let [results (filter #(= transfer-id (:id %)) @resource-db)]
    (last (sort-by :time-stamp results))))