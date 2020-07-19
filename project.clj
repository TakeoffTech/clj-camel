(defproject takeoff/clj-camel "1.0.0"
  :description "Clojure wrapper for Apache Camel"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.0.0"]

                 [org.apache.camel/camel-core "3.4.1"]
                 [org.apache.camel/camel-sql "3.4.1"]
                 [org.apache.camel/camel-jcache "3.4.1"]
                 [org.apache.camel/camel-management "3.4.1"]
                 [malabarba/lazy-map "1.3"]

                 [camel-snake-kebab "0.4.1"]
                 [metosin/jsonista "0.2.6"]
                 [clj-time "0.15.2"]]
  :target-path "target/%s"
  :profiles {:test    {:dependencies [[com.rpl/specter "1.1.3"]
                                      [org.ehcache/ehcache "3.8.1"]
                                      [org.apache.camel/camel-http "3.3.0"]
                                      [org.apache.camel/camel-jsonpath "3.3.0"]]}
             :uberjar {:aot :all}})
