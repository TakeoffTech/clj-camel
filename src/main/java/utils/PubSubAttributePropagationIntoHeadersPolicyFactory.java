package utils;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PubSubAttributePropagationIntoHeadersPolicyFactory implements RoutePolicyFactory {

    private final Map<String, String> mappingFields;

    public PubSubAttributePropagationIntoHeadersPolicyFactory(Map<String, String> mappingFields) {
        this.mappingFields = mappingFields;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        return new PubSubAttributePropagationIntoHeadersRoutePolicy(mappingFields);
    }

    private static final class PubSubAttributePropagationIntoHeadersRoutePolicy extends RoutePolicySupport {
        private static final Logger LOG = LoggerFactory.getLogger(PubSubAttributePropagationIntoHeadersRoutePolicy.class);
        private final Map<String, String> mappingFields;

        PubSubAttributePropagationIntoHeadersRoutePolicy(Map<String, String> mappingFields) {
            this.mappingFields = mappingFields;
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            try {
                final String pubsubAttributesKey = "CamelGooglePubsub.Attributes";

                final Map<String, String> pubsubAttributes = (Map<String, String>) exchange
                        .getIn()
                        .getHeader(pubsubAttributesKey);
                for (Map.Entry<String, String> entry : this.mappingFields.entrySet()) {
                    String value = pubsubAttributes.get(entry.getKey());
                    exchange.getIn().setHeader(entry.getValue(), value);
                }
            } catch (Throwable t) {
                // This exception is ignored
                LOG.trace("PubSubAttributePropagationIntoHeadersRoutePolicy: Failed to propagate attributes", t);
            }
        }
    }
}