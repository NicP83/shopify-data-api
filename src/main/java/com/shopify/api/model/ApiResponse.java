package com.shopify.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper for REST endpoints
 * @param <T> The type of data being returned
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * Indicates if the request was successful
     */
    private boolean success;

    /**
     * Message describing the result
     */
    private String message;

    /**
     * The actual data payload
     */
    private T data;

    /**
     * Error details if any
     */
    private String error;

    /**
     * Create a successful response
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data, null);
    }

    /**
     * Create a successful response with custom message
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, message);
    }

    /**
     * Create an error response with details
     */
    public static <T> ApiResponse<T> error(String message, String errorDetails) {
        return new ApiResponse<>(false, message, null, errorDetails);
    }
}
