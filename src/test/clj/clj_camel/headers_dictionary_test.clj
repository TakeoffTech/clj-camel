(ns clj-camel.headers-dictionary-test
  (:require [clojure.test :refer :all]
            [clj-camel.util :as u]
            [clj-camel.core :as c]))

(defn set-headers-in-processor [{:keys [headers]}]
  {:headers (assoc headers :x-token "test-token"
                           :custom "custom")})

(deftest set-headers-test
  (let [result (u/debug-route {:body "test body"}
                              (c/route-builder (c/from "direct:test")
                                               (c/route-id "test-route")
                                               (c/process (c/copy-body-to-header :header-from-body))
                                               (c/process (c/copy-body-to-header "string-header"))
                                               (c/set-header :x-service-origin (c/constant ":x-service-origin"))
                                               (c/set-header :set-header (c/constant ":set-header value"))
                                               (c/process set-headers-in-processor)
                                               (c/to "direct:result")))]
    (is (= "test-token" (-> result :headers :x-token)))
    (is (= "custom" (-> result :headers :custom)))
    (is (= "test body" (-> result :headers :header-from-body)))
    (is (= nil (-> result :headers :string-header)))
    (is (= "test body" (-> result :headers (get "string-header"))))
    (is (= ":set-header value" (-> result :headers :set-header)))
    (is (= ":x-service-origin" (-> result :headers :x-service-origin)))
    (is (= [[":custom" "custom"]
            [":header-from-body" "test body"]
            [":set-header" ":set-header value"]
            ["string-header" "test body"]
            ["x-service-origin" ":x-service-origin"]
            ["x-token" "test-token"]] (-> result :headers seq)))))
