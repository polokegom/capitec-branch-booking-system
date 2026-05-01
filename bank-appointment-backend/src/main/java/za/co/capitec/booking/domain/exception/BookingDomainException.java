package za.co.capitec.booking.domain.exception;

public abstract class BookingDomainException extends RuntimeException {
  private final int statusCode;
  private final String errorCode;

  protected BookingDomainException(int statusCode, String errorCode, String message) {
    super(message);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
