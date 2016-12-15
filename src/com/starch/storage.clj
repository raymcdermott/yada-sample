(ns com.starch.storage
  (:require [environ.core :refer [env]]
            [datomic.api :as d]
            [clojure.java.io :as io]))

(defonce ^:private db-uri (or (env :datomic-uri) "datomic:mem:/transfers"))

(d/delete-database db-uri)
(d/create-database db-uri)

(def conn (d/connect db-uri))

; TODO move this into tests
;; parse schema edn file
(def schema-tx (read-string (slurp (io/resource "schema.edn"))))

@(d/transact conn schema-tx)

;; parse seed data edn file
(def data-tx (read-string (slurp (io/resource "seed-data.edn"))))

;; submit seed data transaction
@(d/transact conn data-tx)

; TODO Query!
(def db (d/db conn))

; All customers
(println "All customers")
(clojure.pprint/pprint
  (d/q '[:find ?id ?first ?last
         :where
         [?cust :customer/id ?id]
         [?cust :customer/first-name ?first]
         [?cust :customer/last-name ?last]] db))

; A specific customer
(println "Customer Jane")
(clojure.pprint/pprint
  (d/q '[:find [?id ?first ?last]
         :in $ ?first
         :where
         [?cust :customer/id ?id]
         [?cust :customer/first-name ?first]
         [?cust :customer/last-name ?last]] db "Jane"))

; All transfers
(println "All transfers")
(clojure.pprint/pprint
  (d/q '[:find ?first-from ?first-to ?amount
         :where
         [?xfer :transfer/source-customer ?src]
         [?xfer :transfer/target-customer ?to]
         [?xfer :transfer/amount ?amount]
         [?cust-from :customer/id ?src]
         [?cust-from :customer/first-name ?first-from]
         [?cust-to :customer/id ?to]
         [?cust-to :customer/first-name ?first-to]
         ] db))

; All transfers for one customer
(println "All John's transfers")
(clojure.pprint/pprint
  (d/q '[:find ?first ?first-to ?amount
         :in $ ?first
         :where
         [?xfer :transfer/source-customer ?src]
         [?xfer :transfer/target-customer ?to]
         [?xfer :transfer/amount ?amount]
         [?cust-from :customer/id ?src]
         [?cust-from :customer/first-name ?first]
         [?cust-to :customer/id ?to]
         [?cust-to :customer/first-name ?first-to]
         ] db "John"))

;; TODO - txn metadata
{:db/id      (d/tempid :db.part/tx)
 :version    commit-hash
 :command/id command-id}

(defn store-in-datomic
  [transfer]
  (let [txn-data [{:db/id                    (d/tempid :db.part/user)
                   :transfer/id              (:id transfer)
                   :transfer/source-customer (:source-customer transfer)
                   :transfer/target-customer (:target-customer transfer)
                   :transfer/amount          (:amount transfer)}]
        ]
    @(d/transact conn txn-data)))


(def ^:private resource-db (atom #{}))

(defn store-transfer!
  [transfer]
  (swap! resource-db conj transfer)
  transfer)

(defn lookup-transfer
  [transfer-id]
  (when-let [results (filter #(= transfer-id (:id %)) @resource-db)]
    (last (sort-by :time-stamp results))))