(ns clj-camel.headers)

(def dict {:camel-http-uri           "CamelHttpUri"
           :camel-http-query         "CamelHttpQuery"
           :camel-http-method        "CamelHttpMethod"
           :camel-http-response-code "CamelHttpResponseCode"
           :camel-http-response-text "CamelHttpResponseText"
           :camel-filter-matched     "CamelFilterMatched"

           :camel-ssh-stderr         "CamelSshStderr"
           :camel-ssh-exit-value     "CamelSshExitValue"

           :camel-content-type       "Content-Type"

           :fired-time               "firedTime"

           :camel-split-complete     "CamelSplitComplete"
           :camel-split-size         "CamelSplitSize"

           :x-token                  "x-token"
           :x-service-origin         "x-service-origin"})
