package com.shop.tire.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiProblems {
    private final ObjectMapper mapper;

    public ApiProblems(ObjectMapper mapper) { this.mapper = mapper; }

    public static ProblemDetail create(HttpStatus status, String code, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), create(status, code, detail, request.getRequestURI()));
    }
}
