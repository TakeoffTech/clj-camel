(ns clj-camel.idempotent-consumer-test
  (:require [clojure.test :refer [deftest is]]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clj-camel.test-utils :as test-utils]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]))

(deftest idempotent-consumer-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/set-body (c/constant "test"))
                              (c/idempotent-consumer (c/simple "${body}")
                                                     (c/create-memory-idempotent-repository))
                              (c/log "after idempotent-consumer")
                              (c/to "direct:result"))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids))
         (-> "idempotent-consumer.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
