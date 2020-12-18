(defproject takeoff/clj-camel "latest"
  :description "Clojure wrapper for Apache Camel"
  :url "https://github.com/TakeoffTech/clj-camel"
  :license {:name "Apache License Version 2.0"
            :url  "https://www.apache.org/licenses/LICENSE-2.0.txt"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.0.0"]

                 [org.apache.camel/camel-core "3.7.0"]
                 [org.apache.camel/camel-sql "3.7.0"]
                 [org.apache.camel/camel-jcache "3.7.0"]
                 [org.apache.camel/camel-management "3.7.0"]
                 [malabarba/lazy-map "1.3"]

                 [camel-snake-kebab "0.4.1"]
                 [metosin/jsonista "0.2.6"]
                 [clj-time "0.15.2"]]
  :target-path "target/%s"

  :source-paths ["src/main/clj"]
  :test-paths ["src/test/clj" "src/test/resources"]

  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/clojars_user
                                    :password      :env/clojars_pass
                                    :sign-releases false}]]
  
  :profiles {:test    {:dependencies [[com.rpl/specter "1.1.3"]
                                      [org.ehcache/ehcache "3.8.1"]
                                      [org.apache.camel/camel-http "3.7.0"]
                                      [org.apache.camel/camel-jsonpath "3.7.0"]]}
             :uberjar {:aot :all}})
