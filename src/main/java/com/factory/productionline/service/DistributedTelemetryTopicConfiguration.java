package com.factory.productionline.service;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class DistributedTelemetryTopicConfiguration {

    @Bean
    public NewTopic distributedOperationEventsTopic() {
        return new NewTopic(DistributedSimulationTopics.OPERATION_EVENTS_TOPIC, 1, (short) 1);
    }
}
