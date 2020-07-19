(ns clj-camel.camel-map-wrapper
  (:require [clj-camel.headers :refer [dict]])
  (:import (clojure.lang Associative ILookup MapEntry IPersistentCollection Seqable)
           (java.util Map)))

(defn create-key [k]
  (or (get dict k) (str k)))

(deftype CamelMapWrapper [^Map m]
  ILookup
  (valAt [_ k _]
    (get m (create-key k)))

  (valAt [this k]
    (.valAt this k nil))

  Associative
  (assoc [_ k v]
    (CamelMapWrapper. (doto m
                        (.put (create-key k) v))))

  (containsKey [_ k]
    (.containsKey m k))

  (entryAt [this k]
    (when (.containsKey this k)
      (MapEntry/create k (.valAt this k))))

  IPersistentCollection
  (count [_] (.size m))
  (empty [_] (.empty m))
  (cons [_ [k v]]
    (CamelMapWrapper. (.put m k v)))
  (equiv [_ o]
    (and (isa? (class o) CamelMapWrapper)
         (.equals m (.m ^CamelMapWrapper o))))

  Seqable
  (seq [_] (.seq m)))                                       ; todo check seq

(defn camel-map [m]
  (CamelMapWrapper. m))
