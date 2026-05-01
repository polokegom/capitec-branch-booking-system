package za.co.capitec.booking.infrastructure.persistence.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingLookupEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BranchEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingSlotInventoryEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface PersistenceMapper {
  Branch toDomain(BranchEntity entity);

  List<Branch> toBranches(List<BranchEntity> entities);

  @Mapping(target = "startDateTime", expression = "java(toUtc(entity.startDateTime))")
  @Mapping(target = "endDateTime", expression = "java(toUtc(entity.endDateTime))")
  Booking toDomain(BookingEntity entity);

  List<Booking> toBookings(List<BookingEntity> entities);

  @Mapping(target = "updatedAt", source = "createdAt")
  @Mapping(target = "startDateTime", expression = "java(toUtc(booking.startDateTime()))")
  @Mapping(target = "endDateTime", expression = "java(toUtc(booking.endDateTime()))")
  BookingEntity toEntity(Booking booking);

  @Mapping(target = "bookingId", source = "id")
  BookingLookupEntity toLookupEntity(Booking booking);

  BookingSlotAvailability toDomain(BookingSlotInventoryEntity entity);

  List<BookingSlotAvailability> toBookingSlotAvailabilities(List<BookingSlotInventoryEntity> entities);

  default java.time.OffsetDateTime toUtc(java.time.OffsetDateTime value) {
    return BookingDateTimes.toUtc(value);
  }
}
