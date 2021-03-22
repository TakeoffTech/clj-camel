(ns clj-camel.aggregate-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-utils :as test-utils]))

(deftest aggregate-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/set-body (c/constant "test"))
                              (c/aggregate (c/constant 1) c/grouped-body-strategy
                                {:completion-size      1000
                                 :completion-timeout   1000
                                 :completion-predicate (c/predicate (fn [_] true))}
                                (c/log "after aggregating")
                                (c/to "direct:result")))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids))
         (-> "aggregate.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
