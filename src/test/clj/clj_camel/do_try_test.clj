(ns clj-camel.do-try-test
  (:require [clojure.test :refer :all]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-utils :as test-utils]))

(deftest do-try-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/do-try (c/to "http://test-http")
                                        (c/do-catch Exception
                                                    (c/log "handle exception")
                                                    (c/log "handle exception2"))
                                        (c/do-finally
                                          (c/log "finally")
                                          (c/log "finally2")))
                              (c/log "after do-try"))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids)
             (test-utils/remove-expression-definition))
         (-> "do-try.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
