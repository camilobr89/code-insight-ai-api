package com.codeinsight.api.web;

import com.codeinsight.api.domain.Analysis;
import com.codeinsight.api.service.AnalysisService;
import com.codeinsight.api.web.dto.AnalysisResponse;
import com.codeinsight.api.web.dto.AnalyzeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        Analysis analysis = service.analyze(request.repoUrl());
        AnalysisResponse body = AnalysisResponse.from(analysis);
        return ResponseEntity.created(URI.create("/api/analyses/" + analysis.getId())).body(body);
    }

    @GetMapping
    public List<AnalysisResponse> history() {
        return service.history().stream().map(AnalysisResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AnalysisResponse byId(@PathVariable Long id) {
        return service.history().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .map(AnalysisResponse::from)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
    }
}
