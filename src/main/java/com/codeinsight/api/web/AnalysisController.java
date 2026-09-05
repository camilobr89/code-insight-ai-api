package com.codeinsight.api.web;

import com.codeinsight.api.domain.Analysis;
import com.codeinsight.api.service.AnalysisResult;
import com.codeinsight.api.service.AnalysisService;
import com.codeinsight.api.web.dto.AnalysisResponse;
import com.codeinsight.api.web.dto.AnalyzeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
        AnalysisResult result = service.analyze(request.repoUrl(), request.forceRefresh());
        AnalysisResponse body = AnalysisResponse.from(result.analysis(), result.cached());
        return ResponseEntity.created(URI.create("/api/analyses/" + result.analysis().getId())).body(body);
    }

    @GetMapping
    public List<AnalysisResponse> history() {
        return service.history().stream().map(a -> AnalysisResponse.from(a, true)).toList();
    }

    @GetMapping("/{id}")
    public AnalysisResponse byId(@PathVariable Long id) {
        Analysis analysis = service.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        return AnalysisResponse.from(analysis, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        if (!service.deleteById(id)) {
            throw new AnalysisNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        service.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
