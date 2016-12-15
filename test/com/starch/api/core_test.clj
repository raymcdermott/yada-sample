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