package com.swade.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String MRI_PROCESSING_QUEUE = "mri.processing.jobs";

    @Bean
    public Queue mriProcessingQueue() {
        // durable queue so messages survive broker restarts
        return new Queue(MRI_PROCESSING_QUEUE, true);
    }

    /**
     * FIFO behavior is preserved per-queue; to keep processing strictly ordered,
     * we run a single consumer and prefetch=1.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        return factory;
    }
}

