(ns clj-camel.core
  (:refer-clojure :rename {memoize core-memoize
                           filter  core-filter
                           when    core-when})
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure.walk :as w]
            [lazy-map.core :refer [lazy-map]]
            [clj-camel.headers :refer [dict]]
            [clj-camel.camel-map-wrapper :refer [camel-map]])
  (:import (org.apache.camel.model RouteDefinition ProcessorDefinition ChoiceDefinition SplitDefinition)
           (org.apache.camel Exchange Processor Predicate Expression CamelContext NamedNode AggregationStrategy TypeConverter ProducerTemplate LoggingLevel ConsumerTemplate)
           (org.apache.camel.impl DefaultCamelContext)
           (org.apache.camel.builder DeadLetterChannelBuilder RouteBuilder SimpleBuilder Builder ValueBuilder AggregationStrategies)
           (clojure.lang ExceptionInfo)
           (org.apache.camel.model.language JsonPathExpression)
           (org.apache.camel.spi HeaderFilterStrategy IdempotentRepository)
           (java.util Map)
           (org.apache.camel.model ThrottleDefinition SplitDefinition AggregateDefinition ExpressionNode TryDefinition DataFormatDefinition OnCompletionDefinition)
           (org.apache.camel.component.jcache.policy JCachePolicy)
           (javax.sql DataSource)
           (org.apache.camel.processor.idempotent.jdbc JdbcMessageIdRepository)
           (org.apache.camel.support.processor.idempotent MemoryIdempotentRepository)))

(def grouped-exchange-strategy (AggregationStrategies/groupedExchange))
(def grouped-body-strategy (AggregationStrategies/groupedBody))

(def body-expression (Builder/body))
(defn header-expression [name] (Builder/header name))

(defmacro route-builder
  "Initialization single route"
  [& body]
  `(proxy [RouteBuilder] []
     (configure []
       (-> ~'this ~@body))))

(defn from
  "Creates a new route from the given URI input"
  [^RouteBuilder r & [^String uri]]
  (.from r uri))

(defn route-id
  "Set the route id for this route"
  [^RouteDefinition rd & [^String id]]
  (.routeId rd id))

(defn to
  "Sends the exchange to the given endpoint"
  [^RouteDefinition rd & [^String uri {:keys [id]}]]
  (if id
    (.id (.to rd uri) id)
    (.to rd uri)))

(defn convert-body-to
  "Converts the IN message body to the specified type"
  [^RouteDefinition rd & [^Class clazz]]
  (.convertBodyTo rd clazz))

(defn stringify-key [k]
  (or (get dict k) (str k)))

(defn keyword->string
  "Converting clojure keyword to string
  * using dictionary
  * as keyword string ':some-key' if not present in headers.clj"
  [m]
  (w/walk (fn [[k v]] [(stringify-key k) v]) identity m))

(defn fn-name
  "Getting function name for processor id"
  [f]
  (as-> (str f) $
        (or (re-find #"(.+)--\d+@" $)
            (re-find #"(.+)@" $))
        (last $)))

(defn processor
  "Simple wrapper of clojure function for applying Apache Camel processor interface for processing headers, properties, body"
  [f]
  (reify Processor
    (^void process [_ ^Exchange ex]
      (let [{:keys [headers body]} (f (lazy-map {:headers    (camel-map (-> ex .getIn .getHeaders))
                                                 :properties (camel-map
                                                               (-> ex .getProperties))
                                                 :body       (-> ex .getIn .getBody)}))]
        (core-when headers
          (-> ex .getIn (.setHeaders (.m headers))))
        (core-when body
          (-> ex .getIn (.setBody body)))))))

(defn process
  "Adds the custom processor to this destination whichcould be a final destination,
  or could be a transformation in a pipeline"
  [^ProcessorDefinition pd & [f {:keys [id]}]]
  (let [^Processor processor (processor f)]
    (if id
      (.id (.process pd processor) id)
      (.id (.process pd processor) (fn-name f)))))

(defn processor-ex
  "Simple wrapper of clojure function for applying Apache Camel processor interface for processing Exchange"
  [f]
  (reify Processor
    (^void process [_ ^Exchange ex]
      (f ex))))

(defn process-ex
  "Adds the custom processor to this destination which could be a final destination,
  or could be a transformation in a pipeline.
  Can be used for high load routes"
  [^ProcessorDefinition pd & [f {:keys [id]}]]
  (let [^Processor processor (processor-ex f)]
    (if id
      (.id (.process pd processor) id)
      (.id (.process pd processor) (fn-name f)))))

(defn aggregator
  "Simple wrapper for aggregator strategy"
  [f]
  (proxy [AggregationStrategy] []
    (aggregate [^Exchange old ^Exchange new]
      (f old new))))

(defn enrich
  "Enrich an exchange with additional data obtained from a resourceUri
  Read more http://camel.apache.org/content-enricher.html"
  [^ProcessorDefinition pd & [^String uri f]]
  (let [^AggregationStrategy as (aggregator f)]
    (.enrich pd uri as)))

(defn poll-enrich
  "Enrich an exchange with additional data obtained  from a resourceUri using a org.apache.camel.PollingConsumer to poll the endpoint.
  Read more http://camel.apache.org/content-enricher.html"
  [^ProcessorDefinition pd & [^String uri]]
  (.pollEnrich pd uri))

(defn set-header
  "Adds a processor which sets the header on the IN message"
  [^ProcessorDefinition pd & [name ^Expression expr]]
  (.setHeader pd ^String (stringify-key name) expr))

(defn set-body
  "Adds a processor which sets the body on the IN message"
  [^ProcessorDefinition processor-definition & [^Expression expr]]
  (.setBody processor-definition expr))

(defn set-in-body [^Exchange ex body]
  (-> ex .getIn (.setBody body)))

(defn set-in-header [^Exchange ex k value]
  (-> ex .getIn (.setHeader (str k) value)))

(defn simple
  "Creates simple expression
  eg. (c/idempotent-consumer (c/simple '${body}') (c/create-memory-idempotent-repository))"
  [text]
  (SimpleBuilder/simple text))

(defn constant
  "Creates constant expression
  eg. (c/set-body (c/constant 'x-token'))"
  [obj]
  (Builder/constant obj))

(defn json-path
  "Creates constant expression
  eg. (c/set-body (c/json-path '$.data'))"
  [obj]
  (let [exp (JsonPathExpression. obj)]
    (ValueBuilder. exp)))

(defn sub-route
  "Adds possibility to split big route to smallest part"
  [^NamedNode named-node & [f]]
  (f named-node))

(defn predicate
  "Evaluates a binary predicate on the message (headers, properties, body)"
  [f]
  (reify Predicate
    (^boolean matches [_ ^Exchange ex]
      (f (lazy-map {:headers    (camel-map (-> ex .getIn .getHeaders))
                    :properties (camel-map (-> ex .getProperties))
                    :body       (-> ex .getIn .getBody)})))))

(defn predicate-ex
  "Evaluates a binary predicate on the message exchange.
  Can be used for high load routes"
  [f]
  (reify Predicate
    (^boolean matches [_ ^Exchange ex]
      (f ex))))

(defn camel-context
  "Creates the DefaultCamelContext."
  []
  (let [context (DefaultCamelContext.)]
    context))

(defn add-routes
  "Adds route to the camel context"
  [^DefaultCamelContext context & routes]
  (doseq [route routes]
    (.addRoutes context route)))

(defn remove-route
  "Removes route from camel context"
  [^DefaultCamelContext context & [^String route-id]]
  (.removeRoute context route-id))

(defn log
  "Creates a log message to be logged at INFO level."
  [^ProcessorDefinition pd & [^String msg {:keys [id]}]]
  (if id
    (.id (.log pd msg) id)
    (.log pd msg)))

(defn log-warn
  "Creates a log message to be logged at WARN level."
  [^ProcessorDefinition pd & [^String msg {:keys [id]}]]
  (if id
    (.id (.log pd LoggingLevel/WARN msg) id)
    (.log pd LoggingLevel/WARN msg)))

(defn log-error
  "Creates a log message to be logged at ERROR level."
  [^ProcessorDefinition pd & [^String msg {:keys [id]}]]
  (if id
    (.id (.log pd LoggingLevel/ERROR msg) id)
    (.log pd LoggingLevel/ERROR msg)))

(defn copy-body-to-header
  "Copies current body to header with specific key
  eg.  (c/process (c/copy-body-to-header :body-data))"
  [k]
  (fn [{:keys [headers body]}]
    {:headers (assoc headers (stringify-key k) body)}))

(defn send-body-and-headers
  "Sends the body to an endpoint with the specified headers and header values"
  [^ProducerTemplate producer-template ^String endpoint-uri ^Object body ^Map headers]
  (.sendBodyAndHeaders producer-template endpoint-uri body ^Map (keyword->string headers)))

(defn async-request-body-and-headers
  "Sends an asynchronous body to the given endpoint"
  [^ProducerTemplate producer-template ^String endpoint-uri ^Object body ^Map headers]
  (.asyncRequestBodyAndHeaders producer-template endpoint-uri body headers))

(defn request-body-and-headers
  "Sends an synchronous body to the given endpoint"
  [^ProducerTemplate producer-template ^String endpoint-uri ^Object body ^Map headers]
  (.requestBodyAndHeaders producer-template endpoint-uri body headers))

(defmacro split
  "The Splitter from the EIP patterns allows you split a message into a number of pieces and process them individually.
  Read more https://camel.apache.org/components/latest/eips/split-eip.html
  ...
  (c/split (c/json-path '$.data.*') {:agg-strategy        c/grouped-exchange-strategy
                                       :streaming           true
                                       :parallel-processing true}
           (c/process (fn [_] {}) {:id 'dummy-process-1'})
           (c/to 'direct:result' {:id 'result'}))
  (c/process (fn [_] {:body 'after'}))
  ...
  "
  [^ProcessorDefinition pd ^Expression expr opts & body]
  `(-> ~(if (:agg-strategy opts)
          `(.split ~pd ~expr ~(:agg-strategy opts))
          `(.split ~pd ~expr))
       ~@(when-let [id# (:id opts)]
           `((.id ~id#)))
       ~@(core-when (:streaming opts)
           `((.streaming)))
       ~@(core-when (:parallel-processing opts)
           `((.parallelProcessing)))
       ~@(concat body `((.end)))))

(defmacro filter
  "The Message Filter from the EIP patterns allows you to filter messages
  Read more https://camel.apache.org/components/latest/eips/filter-eip.html
  ...
  (c/route-builder (c/from 'direct:test')
  (c/route-id 'test-route')
  (c/to 'http://test-http')
  (c/filter (c/predicate (comp pos? :body))
            (c/log 'Filtered ... ${body}')
            (c/to 'direct:result'))
  (c/process (fn [_] {:body 'after filter'})))
  ..."
  [^ProcessorDefinition pd ^Predicate predicate & body]
  `(-> (.filter ~pd ~predicate)
       ~@(let [id# (-> body first :id)
               body# (if id# (concat `((.id ~id#)) (rest body)) body)]
           (concat body# `((.end))))))

(defmacro memoize
  "Create JCachePolicy interceptor around a route that caches the 'result of the route'
  Read more https://camel.apache.org/components/latest/jcache-component.html#_jcache_policy
  By default the message body at the beginning of the route is the cache key and the body at the end is the stored value.

  ...
  (c/set-body ${body.order-id})
  (log 'order requested: ${body}')
  (c/memoize jcache-x-token-policy
      (log 'getting order with id: ${body}')
      (c/set-body (constant 'result-order')))
  (log 'order found: ${body}')
  ...
  "
  [^ProcessorDefinition pd ^JCachePolicy policy & body]
  `(-> (.policy ~pd ~policy)
       ~@(concat body `((.end)))))

(defmacro throttle
  "The Throttler Pattern allows you to ensure that a specific endpoint does not get overloaded,
  or that we don’t exceed an agreed SLA with some external service.
  Read more https://camel.apache.org/components/latest/eips/throttle-eip.html
  ...
  (c/set-body (c/constant 'test'))
  (c/throttle 20 {:async-delayed      false
                  :reject-execution   false
                  :time-period-millis 10000})
  (c/to 'direct:result')
  ...
  "
  [^ProcessorDefinition pd ^Long requests-number & [{:keys [async-delayed
                                                            reject-execution
                                                            time-period-millis
                                                            executor-service-ref
                                                            correlation-expression
                                                            caller-runs-when-rejected]}]]
  `(-> (.throttle ~pd ~requests-number)
       ~@(core-when (some? async-delayed) `((.asyncDelayed ~async-delayed)))
       ~@(core-when (some? reject-execution) `((.rejectExecution ~reject-execution)))
       ~@(core-when (some? time-period-millis) `((.timePeriodMillis ~time-period-millis)))
       ~@(core-when (some? executor-service-ref) `((.executorServiceRef ~executor-service-ref)))
       ~@(core-when (some? correlation-expression) `((.correlationExpression ~correlation-expression)))
       ~@(core-when (some? caller-runs-when-rejected) `((.callerRunsWhenRejected ~caller-runs-when-rejected)))))

(defmacro aggregate
  "The Aggregator from the EIP patterns allows you to combine a number of messages together into a single message.
  Read more https://camel.apache.org/components/latest/eips/aggregate-eip.html
  ...
  (c/set-body (c/constant 'test'))
  (c/aggregate (c/constant 1) c/grouped-body-strategy
               {:completion-size      1000
                :completion-timeout   1000
                :completion-predicate (c/predicate (fn [_] true))})
  (c/to 'direct:result')
  ...
  "
  [^ProcessorDefinition pd ^Expression expression ^AggregationStrategy strategy opts & body]
  (let [{:keys [completion-size
                completion-timeout
                parallel-processing
                completion-predicate]} opts]
    `(-> (.aggregate ~pd ~expression ~strategy)
         ~@(core-when (some? completion-size) `((.completionSize ~completion-size)))
         ~@(core-when (some? completion-timeout) `((.completionTimeout ~completion-timeout)))
         ~@(core-when (some? parallel-processing) `((.parallelProcessing ~parallel-processing)))
         ~@(core-when (some? completion-predicate) `((.completionPredicate ~completion-predicate)))
         ~@(concat body `((.end))))))

(defn create-jdbc-idempotent-repository
  "Creates a new jdbc based repository"
  [^DataSource datasource ^String processor-name]
  (doto (JdbcMessageIdRepository. datasource processor-name)
    (.setCreateTableIfNotExists false)))

(defn create-memory-idempotent-repository
  "Creates a new memory based repository"
  []
  (MemoryIdempotentRepository/memoryIdempotentRepository))

(defmacro idempotent-consumer
  "The Idempotent Consumer from the EIP patterns is used to filter out duplicate messages.
  Read more https://camel.apache.org/components/latest/eips/idempotentConsumer-eip.html"
  [^ProcessorDefinition pd ^Expression msg-id opts & body]
  (let [{:keys [repo]} opts]
    `(-> ~pd
         (.idempotentConsumer ~msg-id ~repo)
         ~@(concat body `((.end))))))

(defmacro do-try
  "Try... Catch...Finally block
  Read more https://camel.apache.org/manual/latest/try-catch-finally.html
  ...
  (c/do-try
    (c/to 'http://test-http')
    (c/do-catch Exception
      (c/log 'handle exception')
      (c/log 'handle exception2'))
    (c/do-finally
      (c/log 'finally')
      (c/log 'finally2')))
    (c/log 'after do-try')
  ..."
  [^ProcessorDefinition pd & body]
  `(-> (.doTry ~pd)
       ~@(concat body `((.end)))))

(defmacro do-catch
  "Related to do-try macro"
  [^TryDefinition try-def ^Throwable error & body]
  `(-> (.doCatch ~try-def ~error)
       ~@body))

(defmacro do-finally
  "Related to do-try macro"
  [^TryDefinition try-def & body]
  `(-> (.doFinally ~try-def)
       ~@body))

;TODO Move errors handling to separate namespace
(defmulti detect-error-type class)
(defmethod detect-error-type ExceptionInfo [e] (-> (ex-data e) :error-type))
(defmethod detect-error-type NullPointerException [_] :NPE)
(defmethod detect-error-type Exception [e] (-> e type .getName))
(defmethod detect-error-type :default [_] :unknown)

(def on-prepare-failure-processor
  (proxy [Processor] []
    (process [^Exchange ex]
      (let [^Exception e (.getProperty ex Exchange/EXCEPTION_CAUGHT ^Class Exception)
            error-type (detect-error-type e)
            message (.getMessage e)]
        (-> ex .getIn (.setHeaders {":error-type"  error-type
                                    ":error-cause" message}))))))

(defn dead-letter [^RouteDefinition r & [^String uri {:keys [add-exception-message-to-header ;TODO think about more elegant handle map parameters
                                                             maximum-redeliveries
                                                             redelivery-delay
                                                             back-off-multiplier
                                                             use-exponential-backoff]}]]
  (let [dl-builder (cond-> (DeadLetterChannelBuilder. uri)
                           add-exception-message-to-header (.onPrepareFailure on-prepare-failure-processor)
                           maximum-redeliveries (.maximumRedeliveries maximum-redeliveries)
                           redelivery-delay (.redeliveryDelay redelivery-delay)
                           back-off-multiplier (.backOffMultiplier back-off-multiplier)
                           use-exponential-backoff (.useExponentialBackOff))]
    (.errorHandler r dl-builder)))

(defn get-endpoint-uri
  "Get endpoint URI"
  [^Exchange ex]
  (-> ex .getFromEndpoint .getEndpointUri))

(defn get-properties
  "Useful for getting state inside processors"
  [ex]
  (-> ex (.getProperties)))

(defn get-in-headers
  "Useful for getting state inside processors"
  [ex]
  (-> ex (.getIn) (.getHeaders)))

(defn get-in-body
  ([^Exchange ex]
   (-> ex (.getIn) (.getBody)))
  ([^Exchange ex ^Class clazz]
   (-> ex (.getIn) (.getBody clazz))))

(defn get-in-header [^Exchange ex k]
  (-> ex (.getIn) (.getHeader (name k))))

(defn debug-exchange-log [ex]
  (log/warn "------------------------------------------")
  (log/warn "From endpoint:" (get-endpoint-uri ex))
  (log/warn "Properties:" (get-properties ex))
  (log/warn "Input headers:" (get-in-headers ex))
  (log/warn "Input body type:" (type (get-in-body ex)))
  (log/warn "Input body:" (get-in-body ex))
  (log/warn "Is Failed?:" (.isFailed ex))
  (log/warn "Exception" (.getException ex))
  (log/warn "Caught Exception" (get-in-header ex Exchange/EXCEPTION_CAUGHT))
  (log/warn "------------------------------------------"))

(defn debug-exchange [& [^ProcessorDefinition pd]]
  (let [^Processor processor (processor-ex debug-exchange-log)] ; TODO try to use simple proccessor
    (.process pd processor)))

(defmacro type-converter
  "Creates a type converter"
  [& body]
  `(reify TypeConverter
     (convertTo [self type exchange value]
       ~@body)
     (tryConvertTo [self type exchange value]
       ~@body)))

(defn filter-headers
  [headers header-name]
  (if (seq headers)
    (->> (string/lower-case header-name)
         (contains? headers)
         (not))
    true))

(defn header-filter-strategy [to-camel-headers to-external-headers]
  (reify HeaderFilterStrategy
    (applyFilterToCamelHeaders [self header-name header-value exchange]
      (filter-headers to-camel-headers header-name))
    (applyFilterToExternalHeaders [self header-name header-value exchange]
      (filter-headers to-external-headers header-name))))

(defn marshal
  "Marshal the in body using the specified DataFormat"
  [^ProcessorDefinition pd & [^DataFormatDefinition data-format-definition]]
  (.marshal pd data-format-definition))

(defmacro choice
  "The Content Based Router from the EIP patterns allows you to route messages to the correct
  destination based on the contents of the message exchanges.
  Read more https://camel.apache.org/components/latest/eips/choice-eip.html
  ...
  (c/choice (c/when (c/predicate (comp pos? :body))
                    (c/log 'when 1')
                    (c/process some-processor))
            (c/when (c/predicate (comp neg? :body))
                    (c/log 'when 2')
                    (c/process some-processor))
            (c/otherwise
                    (c/log 'otherwise')
                    (c/process some-processor)))
  (c/log 'after choice')
  ..."
  [^ProcessorDefinition pd & body]
  `(-> (.choice ~pd)
       ~@(concat body `((.end)))))

(defmacro when
  "Related to choice"
  [^ChoiceDefinition cd ^Predicate pred & body]
  `(-> (.when ~cd ~pred)
       ~@body))

(defmacro otherwise
  "Related to choice"
  [^ChoiceDefinition cd & body]
  `(-> (.otherwise ~cd)
       ~@body))

(defn unmarshall
  "Unmarshals the in body using the specified DataFormat"
  [^ProcessorDefinition pd & [^DataFormatDefinition data-format-definition]]
  (.unmarshal pd data-format-definition))

(defmacro on-completion
  "Adds a hook that invoke this route as a callback when the
   Exchange has finished being processed."
  [^ProcessorDefinition pd & body]
  `(-> (.onCompletion ~pd)
       ~@(concat body `((.end)))))

(defmacro recipient-list
  "Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
  Params:
    expr – expression to decide the destinations
  Options:
    parallel-processing - if true exchanges processed in parallel
    agg-strategy - aggregation strategy to use
    delimiter – a custom delimiter to use
  Read more https://camel.apache.org/components/latest/eips/recipientList-eip.html"
  [^ProcessorDefinition pd ^Expression expr opts & body]
  (let [{:keys [delimiter agg-strategy parallel-processing]} opts]
    `(->
       ~(if delimiter
          `(.recipientList ~pd ~expr ~delimiter)
          `(.recipientList ~pd ~expr))
       ~@(core-when (some? agg-strategy) `((.aggregationStrategy ~agg-strategy)))
       ~@(core-when parallel-processing
           `((.parallelProcessing)))
       ~@(concat body `((.end))))))

(defn endpoint
  "Resolves the given name to an Endpoint of the specified type.
  If the name has a singleton endpoint registered, then the singleton is returned.
  Otherwise, a new Endpoint is created and registered in the EndpointRegistry."
  [^Exchange exchange uri]
  (-> (.getContext exchange)
      (.getEndpoint uri)))

(defn receive-no-wait
  "Receives from the endpoint, not waiting for a response if non exists."
  [^ConsumerTemplate consumer ^String uri]
  (.receiveNoWait consumer uri))

(defmacro delay
  "Delayer EIP:  Creates a delayer allowing you to delay the delivery of messages to some destination."
  [^ProcessorDefinition pd ^Long delay-in-ms & body]
  `(-> (.delay pd ~delay-in-ms)
       ~@(concat body `((.end)))))

(defn end
  "Ends the current block"
  [^ProcessorDefinition pd]
  (.end pd))

(defn onWhen
  "Sets an additional predicate that should be true before the onCompletion is triggered.
  To be used for fine grained controlling whether a completion callback should be invoked or not"
  [^OnCompletionDefinition pd ^Predicate p]
  (.onWhen pd p))

(defn stop
  "Stops continue routing the current Exchange and marks it as completed."
  [^ProcessorDefinition pd]
  (.stop pd))

(defmacro multicast
  "Multicast EIP:  Multicasts messages to all its child outputs;
  so that each processor and destination gets a copy of the original message to avoid the processors
  interfering with each other."
  [^ProcessorDefinition pd opts & body]
  `(-> (.multicast pd)
       ~@(core-when (:parallel-processing opts)
           `((.parallelProcessing)))
       ~@(concat body `((.end)))))
