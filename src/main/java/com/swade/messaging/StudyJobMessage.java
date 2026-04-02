package com.swade.messaging;

import java.io.Serializable;

public record StudyJobMessage(Long studyId, String filePath) implements Serializable {}
