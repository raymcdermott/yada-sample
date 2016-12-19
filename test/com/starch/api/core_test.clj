(ns com.starch.api.core-test
  (:require [clojure.test :refer :all]
            [http.async.client :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [cheshire.core :refer :all]
            [com.starch.api :refer :all]
            [com.starch.processor :refer :all]))

(def test-port 1234)
(def test-base-url (str "http://localhost:" test-port))

(def customer-1-id #uuid"b27a388d-d693-4d67-89ed-7f75c45bd2d1")
(def customer-2-id #uuid"40624f00-a637-46a8-8142-7489b1a15ff5")

(use-fixtures
  :once
  (fn [f]
    (let [processor (command-channel-processor command->function->event)
          server (-main test-port)]
      (try
        (f)
        (finally
          ((:close server)))))))


(deftest ping-test
  (testing
    (with-open [client (http/create-client)]
      (let [resp (http/GET client (str test-base-url "/hello"))
            status (http/status resp)]
        (http/await resp)
        (is (and (= 200 (:code status))
                 (= "Hello World!" (http/string resp))))))))

(def shared-id (atom ""))

(deftest initiate
  (testing
    (with-open [client (http/create-client)]
      (let [test-params {:customer-source "C1"
                         :customer-target "C2"
                         :amount          123.45}
            resp (http/POST client (str test-base-url "/initiate") :query test-params)
            status (http/status resp)
            _ (http/await resp)
            body (parse-string (http/string resp) true)]

        (reset! shared-id (:id body))

        (is (and (= 200 (:code status))
                 (string? (:id body))))))))

(defn client-post
  [url]
  (with-open [client (http/create-client)]
    (let [test-params {:transfer-id @shared-id}
          resp (http/POST client url :query test-params)
          status (http/status resp)
          _ (http/await resp)
          body (parse-string (http/string resp) true)]

      (is (and (= 200 (:code status))
               (= (:id body) @shared-id))))))

(deftest expire
  (testing
    (client-post (str test-base-url "/expire"))))

(deftest fail
  (testing
    (client-post (str test-base-url "/fail"))))

;; TODO move this into tests
;
;(def ^:private seed-conn (get-conn db-uri))
;
;(d/delete-database db-uri)
;(d/create-database db-uri)
;
;;; parse schema edn file
;(def schema-tx (read-string (slurp (io/resource "schema.edn"))))
;
;@(d/transact seed-conn schema-tx)
;
;;; parse seed data edn file
;(def data-tx (read-string (slurp (io/resource "seed-data.edn"))))
;
;;; submit seed data transaction
;@(d/transact seed-conn data-tx)
;
;; TODO Query!
;(def db (d/db seed-conn))
;
;; All customers
;(println "All customers")
;(clojure.pprint/pprint
;  (d/q '[:find ?id ?first ?last
;         :where
;         [?cust :customer/id ?id]
;         [?cust :customer/first-name ?first]
;         [?cust :customer/last-name ?last]] db))
;
;; A specific customer
;(println "Customer Jane")
;(clojure.pprint/pprint
;  (d/q '[:find [?id ?first ?last]
;         :in $ ?first
;         :where
;         [?cust :customer/id ?id]
;         [?cust :customer/first-name ?first]
;         [?cust :customer/last-name ?last]] db "Jane"))
;
;; All transfers
;(println "All transfers")
;(clojure.pprint/pprint
;  (d/q '[:find ?first-from ?first-to ?amount
;         :where
;         [?xfer :transfer/source-customer ?src]
;         [?xfer :transfer/target-customer ?to]
;         [?xfer :transfer/amount ?amount]
;         [?cust-from :customer/id ?src]
;         [?cust-from :customer/first-name ?first-from]
;         [?cust-to :customer/id ?to]
;         [?cust-to :customer/first-name ?first-to]
;         ] db))
;
;; All transfers for one customer
;(println "All John's transfers")
;(clojure.pprint/pprint
;  (d/q '[:find ?first ?first-to ?amount
;         :in $ ?first
;         :where
;         [?xfer :transfer/source-customer ?src]
;         [?xfer :transfer/target-customer ?to]
;         [?xfer :transfer/amount ?amount]
;         [?cust-from :customer/id ?src]
;         [?cust-from :customer/first-name ?first]
;         [?cust-to :customer/id ?to]
;         [?cust-to :customer/first-name ?first-to]
;         ] db "John"))
;
;; One transfer
;(def xfer-id (d/entity db [:transfer/id #uuid"938eadfd-6a1e-4e71-8da8-55f9fd07d23c"]))
;(clojure.pprint/pprint
;  (d/pull db '[*] (:db/id xfer-id)))
;
