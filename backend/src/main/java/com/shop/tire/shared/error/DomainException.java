package com.shop.tire.shared.error;

public class DomainException extends RuntimeException {
    public enum Kind { INVALID_INPUT, NOT_FOUND, CONFLICT }

    private final Kind kind;
    private final String code;

    public DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}
