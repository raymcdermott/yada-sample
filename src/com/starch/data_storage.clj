(ns com.starch.data-storage)

(def ^:private resource-db (atom #{}))

(defn store-transfer
  [transfer]
  (swap! resource-db conj transfer)
  transfer)

(defn lookup-transfer
  [transfer-id]
  (if-let [results (filter #(= transfer-id (:id %)) @resource-db)]
    (first results)
    (Exception. (str "Cannot find transfer for id " transfer-id))))

