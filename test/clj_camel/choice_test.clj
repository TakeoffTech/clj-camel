(ns clj-camel.choice-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-util :as test-util]))

(defn some-processor [_] {:body "processor body"})

(deftest choice-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/choice (c/when (c/predicate (comp pos? :body))
                                                (c/log "when 1")
                                                (c/process some-processor))
                                        (c/when (c/predicate (comp neg? :body))
                                                (c/log "when 2")
                                                (c/process some-processor))
                                        (c/otherwise
                                          (c/log "otherwise")
                                          (c/process some-processor)))
                              (c/log "after choice"))
             (cu/dump-route-to-xml)
             (test-util/str->input-stream)
             (xml/parse)
             (test-util/remove-ids)
             (test-util/remove-expression-definition))
         (-> "choice.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
