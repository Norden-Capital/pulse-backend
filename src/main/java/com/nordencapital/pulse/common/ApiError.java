package com.nordencapital.pulse.common;

/**
 * JSON body of every error response: {@code {"error":"code","message":"..."}}.
 */
public record ApiError(String error, String message) {
}
