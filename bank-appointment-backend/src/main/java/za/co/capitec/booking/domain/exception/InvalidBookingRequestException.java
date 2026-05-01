package za.co.capitec.booking.domain.exception;

public final class InvalidBookingRequestException extends BookingDomainException {
  public InvalidBookingRequestException(String message) {
    super(400, "invalid_booking_request", message);
  }
}
