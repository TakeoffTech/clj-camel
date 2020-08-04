# Clojure DSL for Apache Camel ![Clojure CI](https://github.com/TakeoffTech/clj-camel/workflows/Clojure%20CI/badge.svg)

## Motivation

Camel is an open source integration framework that empowers you to quickly and easily integrate various systems consuming or producing data.

This library adds a thin layer on top of Java [Apache Camel](https://camel.apache.org) and 
provides a more idiomatic experience of using Apache Camel in the Clojure ecosystem, without changing the original functionality.


## Installation

Include in your project.clj

```clojure
[takeoff/clj-camel "1.0.0"]
```


## Usage

```clojure
(require [clj-camel.core :as c]
         [clj-camel.util :as cu])
```

## Examples

Simple route
```clojure
(c/route-builder (c/from "direct:test")
                 (c/route-id "test-route")
                 (c/set-body (c/constant "test-body"))
                 (c/log "log: ${body}")
                 (c/to "direct:result"))
```

Filter ([original doc](https://camel.apache.org/components/latest/eips/filter-eip.html))
```clojure
(c/route-builder (c/from "direct:test")
            (c/route-id "test-route")
            (c/to "http://test-http")
            (c/filter (c/predicate (comp pos? :body))
                      (c/log "Filtered ... ${body}")
                      (c/to "direct:result"))
            (c/process (fn [_] {:body "after filter"})))
```

Choice ([original doc](https://camel.apache.org/components/latest/eips/choice-eip.html))
```clojure
(c/route-builder (c/choice (c/when (c/predicate (comp pos? :body))
                                   (c/log "when 1")
                                   (c/process some-processor))
                           (c/when (c/predicate (comp neg? :body))
                                   (c/log "when 2")
                                   (c/process some-processor))
                           (c/otherwise
                                   (c/log "otherwise")
                                   (c/process some-processor)))
                 (c/log "after choice"))
```

Split ([original doc](https://camel.apache.org/components/latest/eips/split-eip.html))
```clojure
(c/route-builder (c/from "direct:test")
                 (c/route-id "test-route")
                 (c/process processor1)
                 (c/to "http://test-http")
                 (c/split (c/json-path "$.data.*") {:agg-strategy        c/grouped-exchange-strategy
                                                    :streaming           true
                                                    :parallel-processing true}
                          (c/process (fn [_] {}))
                          (c/filter (c/predicate (comp pos? :reserved-today :body))
                                    (c/log "Filtered ... ${body}")
                                    (c/to "direct:result")))
                 (c/process (fn [_] {:body "after"})))
```

Aggregate ([original doc](https://camel.apache.org/components/latest/eips/aggregate-eip.html))
```clojure
(c/route-builder (c/from "direct:test")
                 (c/set-body (c/constant "test"))
                 (c/aggregate (c/constant 1) c/grouped-body-strategy
                              {:completion-size      1000
                               :completion-timeout   1000
                               :completion-predicate (c/predicate (fn [_] true))})
                 (c/log "after aggregating")
                 (c/to "direct:result"))
```
Caching ([original doc](https://camel.apache.org/components/latest/jcache-component.html))
```clojure
(c/route-builder (c/from "direct:test")
                 (c/route-id "test-route")
                 (c/set-body (c/constant "key"))
                 (c/log "key requested: ${body}")
                 (c/memoize (cu/create-jcache-expiration-policy "cache-name" 60)
                            (c/set-body (c/constant "value"))
                            (c/log "Populate cache with ${body}"))
                 (c/log "key value result: ${body}")
                 (c/to "direct:result"))
```
Throttling ([original doc](https://camel.apache.org/components/latest/eips/throttle-eip.html))
```clojure
(c/route-builder (c/from "direct:test")
                 (c/set-body (c/constant "test"))
                 (c/throttle 20 {:async-delayed      false
                                 :reject-execution   false
                                 :time-period-millis 10000})
                 (c/log "after throttling")
                 (c/to "direct:result"))
``` 
Try/Catch/Finally ([original doc](https://camel.apache.org/manual/latest/try-catch-finally.html))
```clojure
(c/route-builder (c/from "direct:test")
                 (c/route-id "test-route")
                 (c/do-try (c/to "http://test-http")
                           (c/do-catch Exception
                                       (c/log "handle exception")
                                       (c/log "handle exception2"))
                           (c/do-finally
                             (c/log "finally")
                             (c/log "finally2")))
                 (c/log "after do-try"))
```
