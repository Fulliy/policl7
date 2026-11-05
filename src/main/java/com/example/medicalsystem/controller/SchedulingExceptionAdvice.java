package com.example.medicalsystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@ControllerAdvice
public class SchedulingExceptionAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    public Object handleSchedulingError(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null ? "Не удалось выполнить операцию." : exception.getReason();
        if (request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/admin/scheduling/")
                || (request.getContentType() != null && request.getContentType().contains("application/json"))) {
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
        ModelAndView view = new ModelAndView("schedulingError");
        view.setStatus(status);
        view.addObject("message", message);
        return view;
    }
}
