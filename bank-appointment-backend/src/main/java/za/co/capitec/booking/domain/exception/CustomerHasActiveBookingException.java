package za.co.capitec.booking.domain.exception;

public final class CustomerHasActiveBookingException extends BookingDomainException {
  public CustomerHasActiveBookingException(String message) {
    super(409, "customer_has_active_booking", message);
  }
}
