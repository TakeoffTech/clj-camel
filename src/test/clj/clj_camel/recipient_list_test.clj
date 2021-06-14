(ns clj-camel.recipient-list-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-utils :as test-utils]))

(deftest recipient-list-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/set-body (c/constant "x-token"))
                              (c/log "x-token requested: ${body}")
                              (c/recipient-list (c/json-path "$.data.*") {:parallel-processing true
                                                                          :delimiter           ";"
                                                                          :ignore-invalid-endpoints true}
                                (c/set-body (c/constant "result2")))
                              (c/log "Populate cache with ${body}")
                              (c/log "x-token result: ${body}")
                              (c/to "direct:result"))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids))
         (-> "recipient-list.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))

