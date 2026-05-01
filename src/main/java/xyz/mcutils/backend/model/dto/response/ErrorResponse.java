package xyz.mcutils.backend.model.dto.response;

import org.springframework.http.HttpStatus;

import java.util.Date;

/**
 * @param status    The status code of this error.
 * @param code      The HTTP code of this error.
 * @param message   The message of this error.
 * @param timestamp The timestamp this error occurred.
 */
public record ErrorResponse(HttpStatus status, int code, String message, Date timestamp) {
    public ErrorResponse(HttpStatus status, String message) {
        this(status, status.value(), message, new Date());
    }
}
