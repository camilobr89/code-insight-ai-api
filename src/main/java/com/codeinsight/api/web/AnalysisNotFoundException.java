package com.codeinsight.api.web;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(Long id) {
        super("No se encontró el análisis con id " + id);
    }
}
