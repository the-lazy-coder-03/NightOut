package example.org.nightout.controller.api;

public record ApiErrorResponse(
        boolean success,
        String message
) {
    public static ApiErrorResponse failure(String message) {
        return new ApiErrorResponse(false, message);
    }
}
