(ns clj-camel.split-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-util :as test-util]))

(defn processor1 [_] {:body "before"})

(deftest split-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/process processor1)
                              (c/to "http://test-http" {:id "http"})
                              (c/split (c/json-path "$.data.*") {:agg-strategy        c/grouped-exchange-strategy
                                                                 :streaming           true
                                                                 :parallel-processing true
                                                                 :id                  "split-id"}
                                       (c/process (fn [_] {}) {:id "dummy-process-1"})
                                       (c/filter (c/predicate (comp pos? :reserved-today :body)) {:id "filter-id"}
                                                 (c/log "Filtered ... ${body}" {:id "log"})
                                                 (c/to "direct:result" {:id "result"})))
                              (c/process (fn [_] {:body "after"}) {:id "dummy-process-2"}))
             (cu/dump-route-to-xml)
             (test-util/str->input-stream)
             (xml/parse)
             (test-util/remove-expression-definition))
         (-> "split.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
