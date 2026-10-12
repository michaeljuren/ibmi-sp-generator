package com.example.spgen;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.spgen.Models.GenerateRequest;
import com.example.spgen.Models.GenerateResponse;

@RestController
@RequestMapping("/api")
public class GeneratorController {

    private final GeneratorService service;

    public GeneratorController(GeneratorService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        return service.generate(request);
    }

    @RestControllerAdvice
    public static class Errors {
        private static final Logger LOG = LoggerFactory.getLogger(Errors.class);

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }

        @ExceptionHandler(DataAccessException.class)
        public ResponseEntity<Map<String, String>> db(DataAccessException e) {
            LOG.error("IBM i access failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Could not read QSYS2.SYSCOLUMNS from the IBM i. Check the connection settings."));
        }
    }
}
