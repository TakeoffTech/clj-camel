(ns clj-camel.throttle-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-util :as test-util]))

(deftest throttle-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/set-body (c/constant "test"))
                              (c/throttle 20 {:async-delayed      false
                                              :reject-execution   false
                                              :time-period-millis 10000})
                              (c/log "after throttling")
                              (c/to "direct:result"))
             (cu/dump-route-to-xml)
             (test-util/str->input-stream)
             (xml/parse)
             (test-util/remove-ids))
         (-> "throttle.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
