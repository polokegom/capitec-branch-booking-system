package za.co.capitec.booking.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import za.co.capitec.booking.application.command.SaveBranchCommand;
import za.co.capitec.booking.application.utility.TextSanitizer;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BranchEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI, imports = {UUID.class, TextSanitizer.class})
public interface PersistenceMapper {
  Branch toDomain(BranchEntity entity);

  List<Branch> toBranches(List<BranchEntity> entities);

  Booking toDomain(BookingEntity entity);

  List<Booking> toBookings(List<BookingEntity> entities);

  @Mapping(target = "updatedAt", source = "createdAt")
  BookingEntity toEntity(Booking booking);

  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "active", constant = "true")
  @Mapping(target = "code", expression = "java(command.code().trim())")
  @Mapping(target = "name", expression = "java(command.name().trim())")
  @Mapping(target = "city", expression = "java(command.city().trim())")
  @Mapping(target = "country", expression = "java(command.country().trim())")
  @Mapping(target = "province", expression = "java(TextSanitizer.trimToNull(command.province()))")
  @Mapping(target = "address", expression = "java(TextSanitizer.trimToNull(command.address()))")
  @Mapping(target = "adminEmail", source = "assignedAdminEmail")
  BranchEntity toNewEntity(SaveBranchCommand command, String assignedAdminEmail);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "active", constant = "true")
  @Mapping(target = "code", expression = "java(command.code().trim())")
  @Mapping(target = "name", expression = "java(command.name().trim())")
  @Mapping(target = "city", expression = "java(command.city().trim())")
  @Mapping(target = "country", expression = "java(command.country().trim())")
  @Mapping(target = "province", expression = "java(TextSanitizer.trimToNull(command.province()))")
  @Mapping(target = "address", expression = "java(TextSanitizer.trimToNull(command.address()))")
  @Mapping(target = "adminEmail", source = "assignedAdminEmail")
  void updateEntity(@MappingTarget BranchEntity entity, SaveBranchCommand command, String assignedAdminEmail);
}
