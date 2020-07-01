(ns clj-camel.util
  (:require [clj-camel.data.json :as json]
            [clj-camel.core :refer :all]
            [camel-snake-kebab.core :refer [->kebab-case ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.java.io :as io])
  (:import (org.apache.camel.impl DefaultCamelContext)
           (org.apache.camel.api.management ManagedCamelContext)
           (org.apache.camel.component.jcache.policy JCachePolicy)
           (javax.cache.configuration MutableConfiguration)
           (javax.cache.expiry CreatedExpiryPolicy Duration)
           (javax.cache Caching)
           (java.util.concurrent TimeUnit)))

(defn create-jcache-expiration-policy [cache-name ^long seconds]
  (let [conf (-> (MutableConfiguration.)
                 (.setTypes String Object)
                 (.setExpiryPolicyFactory (CreatedExpiryPolicy/factoryOf (Duration. TimeUnit/SECONDS seconds))))
        cache (-> (Caching/getCachingProvider)
                  (.getCacheManager)
                  (.createCache cache-name conf))
        policy (JCachePolicy.)]
    (.setCache policy cache)
    policy))

(defn parse-json [{:keys [body]}]                           ;TODO try to use plumatic/plumbing#bring-on-defnk
  {:body (json/parse body)})

(defn write-json [{:keys [body]}]
  {:body (json/write body)})

(defn kebabify-keys [{:keys [body]}]
  {:body (transform-keys ->kebab-case-keyword body)})

(defn put-message-body-to-map [{:keys [body]}]
  {:body {:data body}})

(defn merge-from-header-to-body [k]
  (fn [{:keys [headers body]}]
    (let [v (get headers k)]
      {:body (assoc body k v)})))

(defn transform-json-to-clojure-map-with-kebabified-keys [named-node]
  (-> named-node
      (process parse-json)
      (process kebabify-keys)))

(def map-to-input-stream-converter
  (type-converter
    (-> value
        (pr-str)
        (.getBytes)
        (io/input-stream))))

(def exchange->map
  (type-converter
    (-> value
        (get-in-body))))

(defn set-start-time [{:keys [headers]}]
  {:headers (assoc headers :start-time (. System (nanoTime)))})

(defn lapse-time [label]
  (fn [{:keys [headers]}]
    (let [start-time (:start-time headers)
          current (. System (nanoTime))]
      {:headers (assoc headers label (str (/ (double (- current start-time)) 1000000000.0) " secs"))})))

(defn debug-route [{:keys [ctx headers body]} route]
  (let [res (atom nil)
        ^DefaultCamelContext ctx (or ctx (camel-context))
        ^ManagedCamelContext managed-ctx (.getExtension ctx ManagedCamelContext)
        pd (.createProducerTemplate ctx)]
    (add-routes ctx
                route
                (route-builder (from "direct:result")
                               (route-id "debug-result-route")
                               (process (fn [msg] (reset! res msg)))
                               (to "mock:mock")))
    (.start ctx)
    (spit "routes.xml" (.dumpRoutesAsXml (.getManagedCamelContext managed-ctx)))
    (send-body-and-headers pd "direct:test" body headers)
    (remove-route ctx "test-route")
    (remove-route ctx "debug-result-route")
    (.shutdown ctx)
    (Thread/sleep 100)
    @res))

(defn dump-route-to-xml [route]
  (let [^DefaultCamelContext ctx (camel-context)
        ^ManagedCamelContext managed-ctx (.getExtension ctx ManagedCamelContext)]
    (add-routes ctx route)
    (.start ctx)
    (let [xml (.dumpRoutesAsXml (.getManagedCamelContext managed-ctx))]
      (.shutdown ctx)
      xml)))
