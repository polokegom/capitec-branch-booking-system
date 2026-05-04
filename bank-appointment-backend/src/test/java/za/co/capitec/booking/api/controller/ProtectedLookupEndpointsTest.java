package za.co.capitec.booking.api.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.application.security.SecurityRoles;

@QuarkusTest
class ProtectedLookupEndpointsTest {
  private static final UUID SANDTON_BRANCH_ID = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");

  @Test
  void anonymousUserCannotAccessProtectedLookupEndpoints() {
    given()
      .when()
      .get("/api/v1/branches")
      .then()
      .statusCode(401);

    given()
      .queryParam("branchId", SANDTON_BRANCH_ID)
      .queryParam("date", nextWeekday().toString())
      .when()
      .get("/api/v1/availability")
      .then()
      .statusCode(401);
  }

  @Test
  @TestSecurity(user = "owner@metadata.co.za", roles = {SecurityRoles.OWNER})
  void ownerCanAccessProtectedLookupEndpoints() {
    assertProtectedLookupEndpointsAreAccessible();
  }

  @Test
  @TestSecurity(user = "admin@metadata.co.za", roles = {SecurityRoles.ADMIN})
  void adminCanAccessProtectedLookupEndpoints() {
    assertProtectedLookupEndpointsAreAccessible();
  }

  @Test
  @TestSecurity(user = "customer@metadata.co.za", roles = {SecurityRoles.CUSTOMER})
  void customerCanAccessProtectedLookupEndpoints() {
    assertProtectedLookupEndpointsAreAccessible();
  }

  private void assertProtectedLookupEndpointsAreAccessible() {
    given()
      .when()
      .get("/api/v1/branches")
      .then()
      .statusCode(200)
      .body("items", notNullValue());

    given()
      .queryParam("branchId", SANDTON_BRANCH_ID)
      .queryParam("date", nextWeekday().toString())
      .when()
      .get("/api/v1/availability")
      .then()
      .statusCode(200)
      .body("$", notNullValue());
  }

  private static LocalDate nextWeekday() {
    LocalDate date = LocalDate.now().plusDays(3);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      date = date.plusDays(1);
    }
    return date;
  }
}