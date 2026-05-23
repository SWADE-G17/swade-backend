package com.swade.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String MRI_PROCESSING_QUEUE = "mri.processing.jobs";

    @Bean
    public Queue mriProcessingQueue() {
        return new Queue(MRI_PROCESSING_QUEUE, true);
    }
}

