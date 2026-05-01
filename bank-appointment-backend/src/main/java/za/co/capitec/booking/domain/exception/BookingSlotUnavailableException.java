package za.co.capitec.booking.domain.exception;

public final class BookingSlotUnavailableException extends BookingDomainException {
  public BookingSlotUnavailableException(String message) {
    super(409, "booking_slot_unavailable", message);
  }
}
