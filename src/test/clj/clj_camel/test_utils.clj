(ns clj-camel.test-utils
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.rpl.specter :as specter]
            [clojure.string :as string])
  (:import (java.io ByteArrayInputStream)))

(defn str->input-stream [str]
  (io/input-stream (ByteArrayInputStream. (.getBytes str))))

(def MAP-VECTOR-NODES
  (specter/recursive-path [] p
                          (specter/cond-path vector? [specter/ALL p]
                                             map? (specter/continue-then-stay specter/MAP-VALS p))))
(defn remove-ids [data]
  (specter/transform [MAP-VECTOR-NODES
                      (specter/must :id)]
                     (fn [_] "1")
                     data))

(defn remove-expression-definition [data]
  (specter/transform [MAP-VECTOR-NODES
                      (specter/selected? [(specter/must :tag) (specter/pred= :expressionDefinition)])
                      (specter/must :content)]
                     (fn [[value]]
                       (->> (string/split value #"\$")
                            (butlast)
                            (string/join "$")
                            (vector)))
                     data))
