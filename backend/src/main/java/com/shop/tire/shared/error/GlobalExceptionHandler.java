package com.shop.tire.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public record FieldError(String field, String message) {}

    @ExceptionHandler(DomainException.class)
    ProblemDetail business(DomainException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ApiProblems.create(status, ex.code(), ex.getMessage(), request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Nieprawidłowy parametr żądania.");
        problem.setProperty("code", "INVALID_PARAMETER");
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException parameter) {
            problem.setProperty("errors",List.of(new FieldError(parameter.getName(),"Nieprawidłowy typ parametru.")));
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, "Nieprawidłowe parametry żądania.");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors",ex.getParameterValidationResults().stream().map(result -> {
            String name=result.getMethodParameter().getParameterName();
            return new FieldError(name==null?"parameter":name,"Nieprawidłowa wartość parametru.");
        }).toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem=body instanceof ProblemDetail detail?detail:ProblemDetail.forStatus(status);
        if (problem.getProperties()==null || !problem.getProperties().containsKey("code")) {
            String code=switch(status.value()) {
                case 400 -> "INVALID_REQUEST";
                case 404 -> "RESOURCE_NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 406 -> "NOT_ACCEPTABLE";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> status.is5xxServerError()?"INTERNAL_ERROR":"HTTP_ERROR";
            };
            problem.setProperty("code",code);
            problem.setDetail(switch(status.value()) {
                case 400 -> "Nieprawidłowe żądanie.";
                case 404 -> "Nie znaleziono zasobu.";
                case 405 -> "Metoda HTTP nie jest obsługiwana dla tego zasobu.";
                case 406 -> "Żądany format odpowiedzi nie jest obsługiwany.";
                case 415 -> "Nieobsługiwany format danych żądania.";
                default -> "Nie udało się obsłużyć żądania.";
            });
        }
        return super.createResponseEntity(problem,headers,status,request);
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail domain(ApiException ex, HttpServletRequest request) {
        return ApiProblems.create(ex.status(), ex.code(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication(HttpServletRequest request) {
        return ApiProblems.create(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Nieprawidłowe dane logowania lub konto jest niedostępne.", request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(HttpServletRequest request) {
        return ApiProblems.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Brak uprawnień.", request.getRequestURI());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(HttpServletRequest request) {
        return ApiProblems.create(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Dane już istnieją lub zostały zmienione. Odśwież dane i spróbuj ponownie.", request.getRequestURI());
    }

    @ExceptionHandler(MailException.class)
    ProblemDetail mail(HttpServletRequest request) {
        return ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                "Nie udało się wysłać wiadomości. Spróbuj ponownie później.", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Popraw nieprawidłowe pola.");
        problem.setProperty("code", "VALIDATION_FAILED");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.isBindingFailure()
                        ? "Nieprawidłowy typ pola." : error.getDefaultMessage())).toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Nieprawidłowy JSON lub nieobsługiwane pola żądania.");
        problem.setProperty("code", "INVALID_REQUEST");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        // Do not log request bodies, database values or token-bearing exception messages.
        log.error("Unexpected request error: {}", ex.getClass().getName());
        return ApiProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Wystąpił błąd serwera.", request.getRequestURI());
    }
}
