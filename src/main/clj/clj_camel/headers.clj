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

           :camel-file-absolute      "CamelFileAbsolute"
           :camel-file-absolute-path "CamelFileAbsolutePath"
           :camel-file-host          "CamelFileHost"
           :camel-file-last-modified "CamelFileLastModified"
           :camel-file-length        "CamelFileLength"
           :camel-file-name          "CamelFileName"
           :camel-file-name-consumed "CamelFileNameConsumed"
           :camel-file-name-only     "CamelFileNameOnly"
           :camel-file-name-produced "CamelFileNameProduced"
           :camel-file-parent        "CamelFileParent"
           :camel-file-path          "CamelFilePath"
           :camel-file-relative-path "CamelFileRelativePath"
           :camel-ftp-reply-code     "CamelFtpReplyCode"
           :camel-ftp-reply-string   "CamelFtpReplyString"

           :x-token                  "x-token"
           :x-service-origin         "x-service-origin"})
