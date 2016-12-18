(ns com.starch.storage
  (:require [environ.core :refer [env]]
            [datomic.api :as d]
            [clojure.java.io :as io]))

(defonce ^:private db-uri (or (env :datomic-uri) "datomic:mem:/transfers"))

(d/delete-database db-uri)
(d/create-database db-uri)

(defn get-conn
  ([]                                                       ; TODO - drop 0 arity, others must provide URI
   (d/connect db-uri))
  ([uri]
   (d/connect uri)))

(def ^:private seed-conn (get-conn db-uri))

; TODO move this into tests
;; parse schema edn file
(def schema-tx (read-string (slurp (io/resource "schema.edn"))))

@(d/transact seed-conn schema-tx)

;; parse seed data edn file
(def data-tx (read-string (slurp (io/resource "seed-data.edn"))))

;; submit seed data transaction
@(d/transact seed-conn data-tx)

; TODO Query!
(def db (d/db seed-conn))

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

; One transfer
(def xfer-id (d/entity db [:transfer/id #uuid"938eadfd-6a1e-4e71-8da8-55f9fd07d23c"]))
(clojure.pprint/pprint
  (d/pull db '[*] (:db/id xfer-id)))


(defn store-in-datomic!
  "Add the data and return the transaction result"          ; TODO make the return value more friendly
  [conn transfer]
  (let [txn-data [{:db/id                    (or (:db/id transfer) (d/tempid :db.part/user))
                   :transfer/id              (:id transfer)
                   :transfer/time-stamp      (:time-stamp transfer)
                   :transfer/source-customer (:source-customer transfer)
                   :transfer/target-customer (:target-customer transfer)
                   :transfer/amount          (:amount transfer)
                   :transfer/expired         (or (:expired transfer) false)
                   :transfer/failed          (or (:failed transfer) false)
                   :code/version             (:version transfer)
                   :command/id               (:command-id transfer)}]]
    (when @(d/transact conn txn-data)
      transfer)))

(defn lookup-in-datomic
  [conn transfer-id]
  (let [db (d/db conn)]
    (when-let [entity (d/entity db [:transfer/id transfer-id])]
      (d/pull db '[*] (:db/id entity)))))

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