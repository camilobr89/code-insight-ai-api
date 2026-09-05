package com.codeinsight.api.web;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(UUID id) {
        super("No se encontró el análisis con id " + id);
    }
}
