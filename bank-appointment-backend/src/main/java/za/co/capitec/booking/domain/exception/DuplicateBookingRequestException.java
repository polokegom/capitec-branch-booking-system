package za.co.capitec.booking.domain.exception;

public final class DuplicateBookingRequestException extends RuntimeException {
  public DuplicateBookingRequestException(String message) {
    super(message);
  }
}
