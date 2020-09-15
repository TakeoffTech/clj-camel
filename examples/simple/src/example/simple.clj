(ns example.simple
  (:require [clj-camel.core :as c])
  (:gen-class))

(def route1 (c/route-builder (c/from "timer:test?repeatCount=10")
                             (c/log "route1 ${body}")
                             (c/to "direct:test")))

(def route2 (c/route-builder (c/from "direct:test")
                             (c/route-id "test-route")
                             (c/set-body (c/constant "test-body"))
                             (c/log "route2: ${body}")))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (let [ctx (c/camel-context)]
    (c/add-routes ctx route1 route2)
    (.start ctx)
    (Thread/sleep 10000)
    (.shutdown ctx)))
