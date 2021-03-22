package utils;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.engine.MDCUnitOfWork;
import org.apache.camel.support.PatternHelper;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class MDCFromHeadersUnitOfWork extends MDCUnitOfWork {
    private final Map<String, String> mappingFields;
    private final Map<String, String> originalHeadersFields = new HashMap<>();

    public MDCFromHeadersUnitOfWork(Exchange exchange, Map<String, String> mappingFields) {
        super(exchange, exchange.getContext().getInflightRepository(), exchange.getContext().getMDCLoggingKeysPattern(),
                exchange.getContext().isAllowUseOriginalMessage(), exchange.getContext().isUseBreadcrumb());
        this.mappingFields = mappingFields;
    }

    @Override
    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        // add optional step id
        String stepId = exchange.getProperty(Exchange.STEP_ID, String.class);
        if (stepId != null) {
            MDC.put(MDC_STEP_ID, stepId);
        }

        for (Map.Entry<String, String> entry : this.mappingFields.entrySet()) {
            String mdcKey = entry.getKey();
            String exchangeHeaderKey = entry.getValue();
            originalHeadersFields.put(mdcKey, MDC.get(mdcKey));
            Object headerValue = exchange.getMessage().getHeader(exchangeHeaderKey);
            if (headerValue != null) {
                MDC.put(mdcKey, String.valueOf(headerValue));
            }
        }

        return new CustomMDCCallback(callback, exchange.getContext().getMDCLoggingKeysPattern(), mappingFields);
    }

    @Override
    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        super.afterProcess(processor, exchange, callback, doneSync);
        restoreOriginalMDCValues();
    }

    @Override
    public void clear() {
        super.clear();
        restoreOriginalMDCValues();
    }

    private void restoreOriginalMDCValues() {
        for (Map.Entry<String, String> entry : mappingFields.entrySet()) {
            String mdcKey = entry.getKey();
            String originalMDCValue = this.originalHeadersFields.get(mdcKey);
            if (originalMDCValue != null) {
                MDC.put(mdcKey, originalMDCValue);
            } else {
                MDC.remove(mdcKey);
            }
        }
    }

    private static boolean matchPatterns(String value, String[] patterns) {
        for (String pattern : patterns) {
            if (PatternHelper.matchPattern(value, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link AsyncCallback} which preserves {@link MDC} when
     * the asynchronous routing engine is being used.
     */
    private static final class CustomMDCCallback implements AsyncCallback {

        private final AsyncCallback delegate;
        private final String breadcrumbId;
        private final String exchangeId;
        private final String messageId;
        private final String correlationId;
        private final String routeId;
        private final String camelContextId;
        private final Map<String, String> custom;
        private final Map<String, String> callbackOriginalHeadersFields = new HashMap<>();
        private final Map<String, String> callbackMappingFields;

        private CustomMDCCallback(AsyncCallback delegate, String pattern, Map<String, String> mappingFields) {
            this.callbackMappingFields = mappingFields;

            this.delegate = delegate;
            this.exchangeId = MDC.get(MDC_EXCHANGE_ID);
            this.messageId = MDC.get(MDC_MESSAGE_ID);
            this.breadcrumbId = MDC.get(MDC_BREADCRUMB_ID);
            this.correlationId = MDC.get(MDC_CORRELATION_ID);
            this.camelContextId = MDC.get(MDC_CAMEL_CONTEXT_ID);
            this.routeId = MDC.get(MDC_ROUTE_ID);

            if (pattern != null) {
                custom = new HashMap<>();
                Map<String, String> mdc = MDC.getCopyOfContextMap();
                if (mdc != null) {
                    if ("*".equals(pattern)) {
                        custom.putAll(mdc);
                    } else {
                        final String[] patterns = pattern.split(",");
                        mdc.forEach((k, v) -> {
                            if (matchPatterns(k, patterns)) {
                                custom.put(k, v);
                            }
                        });
                    }
                }
            } else {
                custom = null;
            }

            for (Map.Entry<String, String> entry : mappingFields.entrySet()) {
                String mdcKey = entry.getKey();
                callbackOriginalHeadersFields.put(mdcKey, MDC.get(mdcKey));
            }
        }

        public void done(boolean doneSync) {
            try {
                if (!doneSync) {
                    // when done asynchronously then restore information from previous thread
                    if (breadcrumbId != null) {
                        MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
                    }
                    if (exchangeId != null) {
                        MDC.put(MDC_EXCHANGE_ID, exchangeId);
                    }
                    if (messageId != null) {
                        MDC.put(MDC_MESSAGE_ID, messageId);
                    }
                    if (correlationId != null) {
                        MDC.put(MDC_CORRELATION_ID, correlationId);
                    }
                    if (camelContextId != null) {
                        MDC.put(MDC_CAMEL_CONTEXT_ID, camelContextId);
                    }
                    if (custom != null) {
                        custom.forEach(MDC::put);
                    }

                    for (Map.Entry<String, String> entry : callbackMappingFields.entrySet()) {
                        String mdcKey = entry.getKey();
                        String originalMDCValue = this.callbackOriginalHeadersFields.get(mdcKey);
                        if (originalMDCValue != null) {
                            MDC.put(mdcKey, originalMDCValue);
                        }
                    }
                }
                // need to setup the routeId finally
                if (routeId != null) {
                    MDC.put(MDC_ROUTE_ID, routeId);
                }

            } finally {
                // muse ensure delegate is invoked
                delegate.done(doneSync);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}