package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;

public interface TransferEventPublisher {

    void ensureTopics(int operationsCount);

    void publish(ProductionLine.KafkaTransferMessage message);
}
