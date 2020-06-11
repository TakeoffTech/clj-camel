(ns clj-camel.filter-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-util :as test-util]))

(deftest filter-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/to "http://test-http")
                              (c/filter (c/predicate (comp pos? :body))
                                        (c/log "Filtered ... ${body}")
                                        (c/to "direct:result"))
                              (c/process (fn [_] {:body "after filter"})))
             (cu/dump-route-to-xml)
             (test-util/str->input-stream)
             (xml/parse)
             (test-util/remove-ids)
             (test-util/remove-expression-definition))
         (-> "filter.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
