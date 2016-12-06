(ns com.starch.storage)

; TODO replace with Datomic

(def ^:private resource-db (atom #{}))

(defn store-transfer!
  [transfer]
  (swap! resource-db conj transfer)
  transfer)

(defn lookup-transfer
  [transfer-id]
  (when-let [results (filter #(= transfer-id (:id %)) @resource-db)]
    (last (sort-by :time-stamp results))))