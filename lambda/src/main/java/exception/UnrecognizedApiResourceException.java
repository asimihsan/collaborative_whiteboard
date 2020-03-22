package exception;

public class UnrecognizedApiResourceException extends RuntimeException {
    public UnrecognizedApiResourceException(final String message) {
        super(message);
    }
}
