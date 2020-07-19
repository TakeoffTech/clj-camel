(ns clj-camel.data.time
  (:require [clj-time.format :as f]))

(defn to-utc-string-or-nil [date-time]
  (some->> date-time
           (f/unparse (f/formatter :date-time))))
