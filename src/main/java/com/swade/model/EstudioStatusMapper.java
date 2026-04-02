package com.swade.model;

import java.util.Map;

public final class EstudioStatusMapper {

    private static final Map<String, StudyStatus> DB_TO_API = Map.of(
            "esperando", StudyStatus.QUEUED,
            "procesando", StudyStatus.PROCESSING,
            "listo", StudyStatus.COMPLETED,
            "error", StudyStatus.FAILED
    );

    private static final Map<StudyStatus, String> API_TO_DB = Map.of(
            StudyStatus.QUEUED, "esperando",
            StudyStatus.PROCESSING, "procesando",
            StudyStatus.COMPLETED, "listo",
            StudyStatus.FAILED, "error"
    );

    private EstudioStatusMapper() {}

    public static StudyStatus toApi(String dbStatus) {
        return DB_TO_API.getOrDefault(dbStatus, StudyStatus.QUEUED);
    }

    public static String toDb(StudyStatus apiStatus) {
        return API_TO_DB.getOrDefault(apiStatus, "esperando");
    }
}
