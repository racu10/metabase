(ns metabase.test.data.druid
  (:require [metabase.test.data.interface :as tx]))

(tx/add-test-extensions! :druid)

(defmethod tx/dbdef->connection-details :druid
  [& _]
  ;; disable the env var for now so the value in CircleCI (our ec2 server) doesn't stomp on the new localhost one
  {:host "http://localhost" #_(tx/db-test-env-var-or-throw :druid :host "http://localhost")
   :port (Integer/parseUnsignedInt (tx/db-test-env-var-or-throw :druid :port "8082"))})

(defmethod tx/create-db! :druid
  [& _]
  nil)
