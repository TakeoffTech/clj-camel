(ns clj-camel.util
  (:require [clj-camel.core :as c]
            [clj-camel.data.json :as json]
            [camel-snake-kebab.core :refer [->kebab-case ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.java.io :as io])
  (:import (org.apache.camel.impl DefaultCamelContext)
           (org.apache.camel.api.management ManagedCamelContext)
           (org.apache.camel.component.jcache.policy JCachePolicy)
           (javax.cache.configuration MutableConfiguration)
           (javax.cache.expiry CreatedExpiryPolicy Duration)
           (javax.cache Caching)
           (java.util.concurrent TimeUnit)
           (org.apache.camel.component.file.remote SftpEndpoint RemoteFileOperations RemoteFileEndpoint)
           (utils MDCFromHeadersUnitOfWorkFactory PubSubAttributePropagationIntoHeadersPolicyFactory)))

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
      (c/process parse-json)
      (c/process kebabify-keys)))

(def map-to-input-stream-converter
  (c/type-converter
    (-> value
        (pr-str)
        (.getBytes)
        (io/input-stream))))

(def exchange->map
  (c/type-converter
    (-> value
        (c/get-in-body))))

(defn set-start-time [{:keys [headers]}]
  {:headers (assoc headers :start-time (. System (nanoTime)))})

(defn lapse-time [label]
  (fn [{:keys [headers]}]
    (let [start-time (:start-time headers)
          current (. System (nanoTime))]
      {:headers (assoc headers label (str (/ (double (- current start-time)) 1000000000.0) " secs"))})))

(defn debug-route [{:keys [ctx headers body]} route]
  (let [res (atom nil)
        ^DefaultCamelContext ctx (or ctx (c/camel-context))
        ^ManagedCamelContext managed-ctx (.getExtension ctx ManagedCamelContext)
        pd (.createProducerTemplate ctx)]
    (c/add-routes ctx
                  route
                  (c/route-builder (c/from "direct:result")
                                   (c/route-id "debug-result-route")
                                   (c/process (fn [msg] (reset! res msg)))
                                   (c/to "mock:mock")))
    (.start ctx)
    (spit "routes.xml" (.dumpRoutesAsXml (.getManagedCamelContext managed-ctx)))
    (c/send-body-and-headers pd "direct:test" body headers)
    (c/remove-route ctx "test-route")
    (c/remove-route ctx "debug-result-route")
    (.shutdown ctx)
    (Thread/sleep 100)
    @res))

(defn dump-route-to-xml [route]
  (let [^DefaultCamelContext ctx (c/camel-context)
        ^ManagedCamelContext managed-ctx (.getExtension ctx ManagedCamelContext)]
    (c/add-routes ctx route)
    (.start ctx)
    (let [xml (.dumpRoutesAsXml (.getManagedCamelContext managed-ctx))]
      (.shutdown ctx)
      xml)))

(defn full-path
  "Get full path of GenericFileConfiguration"
  [^RemoteFileEndpoint endpoint file-name]
  (-> endpoint
      (.getConfiguration)
      (.getDirectory)
      (str "/" file-name)))

(defn retrieve-file
  "Retrieves the file"
  [^RemoteFileOperations ops path exchange]
  (.retrieveFile ops path exchange -1))

(defmacro with-file-operations
  "Creates scope with connection to Remote File Storage (e.g FTP)
   Out of scope connection is automatically closed
   Exposes variable ops in macro body"
  [^RemoteFileEndpoint endpoint ex & body]
  `(let [~'ops (.createRemoteFileOperations ~endpoint)]
     (try
       (.connect ~'ops (.getConfiguration ~endpoint) ~ex)
       ~@body
       (finally
         (.disconnect ~'ops)))))

(defn set-customer-uow-with-mdc-from-headers [^DefaultCamelContext context mapping]
  {:pre [(map? mapping)]}
  (.setUnitOfWorkFactory context (MDCFromHeadersUnitOfWorkFactory. mapping)))

(defn set-pubsub-attributes-propagation [^DefaultCamelContext context mapping]
  {:pre [(map? mapping)]}
  (.addRoutePolicyFactory context (PubSubAttributePropagationIntoHeadersPolicyFactory. mapping)))
