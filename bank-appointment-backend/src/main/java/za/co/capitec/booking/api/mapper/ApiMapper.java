package za.co.capitec.booking.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import za.co.capitec.booking.api.dto.BookingDetailResponse;
import za.co.capitec.booking.api.dto.BookingResponse;
import za.co.capitec.booking.api.dto.BranchResponse;
import za.co.capitec.booking.api.dto.BranchAdminRequest;
import za.co.capitec.booking.api.dto.CreateBookingRequest;
import za.co.capitec.booking.api.dto.BookingSlotAvailabilityResponse;
import za.co.capitec.booking.application.command.CreateBookingCommand;
import za.co.capitec.booking.application.command.SaveBranchCommand;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingDetails;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface ApiMapper {

  BranchResponse toBranchResponse(Branch branch);

  SaveBranchCommand toSaveBranchCommand(BranchAdminRequest request);

  default BookingSlotAvailabilityResponse toBookingSlotAvailabilityResponse(BookingSlotAvailability bookingSlotAvailability) {
    if (bookingSlotAvailability == null) {
      return null;
    }
    return new BookingSlotAvailabilityResponse(
      bookingSlotAvailability.branchId(),
      BookingDateTimes.toDateTime(bookingSlotAvailability.appointmentDate(), bookingSlotAvailability.bookingSlotStartTime()),
      BookingDateTimes.toDateTime(
        bookingSlotAvailability.appointmentDate(),
        bookingSlotAvailability.bookingSlotStartTime().plusMinutes(Branch.SLOT_MINUTES)
      )
    );
  }

  @Mapping(target = "bookingId", source = "id")
  @Mapping(target = "status", expression = "java(booking.status() == null ? null : booking.status().name())")
  BookingResponse toBookingResponse(Booking booking);

  @Mapping(target = "id", source = "booking.id")
  @Mapping(target = "bookingReference", source = "booking.bookingReference")
  @Mapping(target = "branchId", source = "booking.branchId")
  @Mapping(target = "branchName", expression = "java(branch == null ? null : branch.name())")
  @Mapping(target = "branchCity", expression = "java(branch == null ? null : branch.city())")
  @Mapping(target = "branchCountry", expression = "java(branch == null ? null : branch.country())")
  @Mapping(target = "customerName", source = "booking.customerName")
  @Mapping(target = "customerEmail", source = "booking.customerEmail")
  @Mapping(target = "preferredLanguage", source = "booking.preferredLanguage")
  @Mapping(target = "status", expression = "java(booking.status() == null ? null : booking.status().name())")
  @Mapping(target = "createdAt", source = "booking.createdAt")
  BookingDetailResponse toBookingDetailResponse(Booking booking, Branch branch);

  @Mapping(target = "id", source = "booking.id")
  @Mapping(target = "bookingReference", source = "booking.bookingReference")
  @Mapping(target = "branchId", source = "booking.branchId")
  @Mapping(target = "branchName", expression = "java(details.branch() == null ? null : details.branch().name())")
  @Mapping(target = "branchCity", expression = "java(details.branch() == null ? null : details.branch().city())")
  @Mapping(target = "branchCountry", expression = "java(details.branch() == null ? null : details.branch().country())")
  @Mapping(target = "startDateTime", source = "booking.startDateTime")
  @Mapping(target = "endDateTime", source = "booking.endDateTime")
  @Mapping(target = "customerName", source = "booking.customerName")
  @Mapping(target = "customerEmail", source = "booking.customerEmail")
  @Mapping(target = "preferredLanguage", source = "booking.preferredLanguage")
  @Mapping(target = "status", expression = "java(details.booking().status() == null ? null : details.booking().status().name())")
  @Mapping(target = "createdAt", source = "booking.createdAt")
  BookingDetailResponse toBookingDetailResponse(BookingDetails details);

  @Mapping(target = "idempotencyKey", source = "idempotencyKey")
  CreateBookingCommand toCreateBookingCommand(CreateBookingRequest request, String idempotencyKey);
}
