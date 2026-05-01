package za.co.capitec.booking.domain.exception;

public final class BookingNotFoundException extends BookingDomainException {
  public BookingNotFoundException(String bookingReference) {
    super(404, "booking_not_found", "Booking '%s' was not found.".formatted(bookingReference));
  }
}
