package com.swade.messaging;

import com.swade.service.StudyService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StudyJobConsumer {

    private final StudyService studyService;

    public StudyJobConsumer(StudyService studyService) {
        this.studyService = studyService;
    }

    @RabbitListener(queues = RabbitMqConfig.MRI_PROCESSING_QUEUE, autoStartup = "${swade.rabbitmq.listener.enabled:true}")
    public void consume(StudyJobMessage message) {
        if (message == null || message.studyId() == null
                || message.filePath() == null || message.filePath().isBlank()) {
            return;
        }
        studyService.processJob(message.studyId(), message.filePath());
    }
}
