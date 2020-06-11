(ns clj-camel.throttle-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-utils :as test-utils]))

(deftest throttle-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/set-body (c/constant "test"))
                              (c/throttle 20 {:async-delayed      false
                                              :reject-execution   false
                                              :time-period-millis 10000})
                              (c/log "after throttling")
                              (c/to "direct:result"))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids))
         (-> "throttle.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
