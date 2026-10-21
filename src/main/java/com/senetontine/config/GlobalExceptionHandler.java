package com.senetontine.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

// SANS ce handler, le champ "message" du JSON d'erreur est vide par defaut dans Spring Boot
// (server.error.include-message=never, choix de securite par defaut du framework) : tous les
// messages soigneusement rediges dans le code (ResponseStatusException et validations @Valid -
// "Numero ou mot de passe incorrect", "Le mot de passe doit contenir au moins 8 caracteres",
// "Trop de tentatives de connexion"...) n'arrivaient JAMAIS jusqu'a l'app mobile. Le front
// (app/api/client.ts) lit data.message et retombait a chaque fois sur un message generique
// "Erreur serveur (xxx)", quel que soit le vrai probleme. Ce handler garantit un corps JSON
// {"message": "..."} coherent et exploitable cote app.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return body(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    // Erreurs de validation @Valid (DTO d'entree) : on renvoie le premier message de champ en
    // clair plutot que le message technique verbeux que produirait le comportement par defaut.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Requête invalide");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.value());
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
