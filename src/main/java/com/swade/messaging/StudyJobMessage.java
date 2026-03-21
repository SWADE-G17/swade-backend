package com.swade.messaging;

import java.io.Serializable;

public record StudyJobMessage(String studyId, String filePath) implements Serializable {}

