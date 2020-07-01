(ns clj-camel.memoize-test
  (:require [clojure.test :refer [deftest is]]
            [clj-camel.core :as c]
            [clj-camel.util :as cu]
            [clojure.xml :as xml]
            [clojure.data]
            [clojure.java.io :as io]
            [clj-camel.test-utils :as test-utils]))

(deftest memoize-route-test
  (is (= (-> (c/route-builder (c/from "direct:test")
                              (c/route-id "test-route")
                              (c/set-body (c/constant "x-token"))
                              (c/log "x-token requested: ${body}")
                              (c/memoize (cu/create-jcache-expiration-policy "test" 60)
                                         (c/set-body (c/constant "result2"))
                                         (c/log "Populate cache with ${body}"))
                              (c/log "x-token result: ${body}")
                              (c/to "direct:result"))
             (cu/dump-route-to-xml)
             (test-utils/str->input-stream)
             (xml/parse)
             (test-utils/remove-ids))
         (-> "memoize.xml"
             (io/resource)
             (io/input-stream)
             (xml/parse)))))
