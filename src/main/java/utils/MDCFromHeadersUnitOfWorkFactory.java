package utils;

import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;

import java.util.Map;

public class MDCFromHeadersUnitOfWorkFactory implements UnitOfWorkFactory {

    private final Map<String, String> headerFields;

    public MDCFromHeadersUnitOfWorkFactory(Map<String, String> headerFields) {
        this.headerFields = headerFields;
    }

    public UnitOfWork createUnitOfWork(Exchange exchange) {
        return exchange.getContext().isUseMDCLogging() ?
                new MDCFromHeadersUnitOfWork(exchange, this.headerFields) :
                new DefaultUnitOfWork(exchange);
    }
}