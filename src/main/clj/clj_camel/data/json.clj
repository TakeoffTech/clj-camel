(ns clj-camel.data.json
  (:require [jsonista.core :as json]
            [clj-camel.data.time :as time])
  (:import (com.fasterxml.jackson.databind SerializationFeature)
           (org.joda.time DateTime)
           (java.util Date)
           (org.apache.camel Exchange)))

(def encoders
  {Date     (fn [dt gen] (.writeString gen (-> (DateTime. dt) time/to-utc-string-or-nil)))
   DateTime (fn [dt gen] (.writeString gen (-> dt time/to-utc-string-or-nil)))
   Exchange (fn [ex gen] (.writeString gen (.getExchangeId ex)))})

(def mapper
  (-> {:decode-key-fn keyword :encoders encoders}
      (json/object-mapper)
      (.disable SerializationFeature/FAIL_ON_EMPTY_BEANS)))

(defn parse [object]
  (json/read-value object mapper))

(defn write [object]
  (json/write-value-as-string object mapper))
