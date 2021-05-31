(ns clj-camel.test-util
  (:require [clj-camel.core :as c])
  (:import (org.apache.camel.builder AdviceWithRouteBuilder RouteBuilder AdviceWith)
           (org.apache.camel.model InterceptSendToEndpointDefinition)
           (org.apache.camel.impl DefaultCamelContext)))

(defmacro mock-camel-endpoint
  " A RouteBuilder which has extended capabilities when using the advice with feature.
  Read more http://camel.apache.org/advicewith.html"
  [& body]
  `(proxy [AdviceWithRouteBuilder] []
     (configure []
       (-> ~'this ~@body))))

(defn intercept-send-to-endpoint
  "Applies a route for an interceptor if an exchange is send to the given endpoint"
  [^RouteBuilder r & [^String uri]]
  (.interceptSendToEndpoint r uri))

(defn skip-send-to-original-endpoint
  "Skip sending the ex to the original intended endpoint"
  [& [^InterceptSendToEndpointDefinition r]]
  (.skipSendToOriginalEndpoint r))

(defn mock-endpoint
  "Mock camel endpoint with custom processor"
  [uri processor]
  (mock-camel-endpoint
    (intercept-send-to-endpoint uri)
    (skip-send-to-original-endpoint)
    (c/process processor)))

(defn advice-route-with
  "Advices this route with the route builder."
  [ctx name uri processor]
  (AdviceWith/adviceWith (.getRouteDefinition ctx name) ctx (mock-endpoint uri processor)))
