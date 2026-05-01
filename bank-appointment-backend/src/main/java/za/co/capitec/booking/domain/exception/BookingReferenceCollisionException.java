package za.co.capitec.booking.domain.exception;

public final class BookingReferenceCollisionException extends RuntimeException {
  public BookingReferenceCollisionException(String message) {
    super(message);
  }
}
