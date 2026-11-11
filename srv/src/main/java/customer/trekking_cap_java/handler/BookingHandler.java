
package customer.trekking_cap_java.handler;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.trekkingservice.Bookings;

@Component
@ServiceName("TrekkingService")
public class BookingHandler implements EventHandler {

    private final PersistenceService db;

    public BookingHandler(PersistenceService db) {
        this.db = db;
    }

    @Before(event = CqnService.EVENT_CREATE, entity = "TrekkingService.Bookings")
    public void beforeCreateBooking(List<Bookings> bookings) {

        for (Bookings booking : bookings) {

            String tourId = booking.getTourId();
            Integer participants = booking.getParticipants();

            // 1. Validate request
            if (tourId == null || tourId.isBlank()) {
                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Tour ID is required");
            }

            if (participants == null || participants <= 0) {
                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Participants must be greater than zero");
            }

            // 2. Find and lock tour
            var tour = db.run(
                    Select.from("trekking.Tours")
                            .byId(tourId)
                            .lock())
                    .first().orElseThrow(() -> new ServiceException(
                            ErrorStatuses.NOT_FOUND,
                            "Tour not found"));

            // 3. Check tour status
            if (!"OPEN".equals(tour.get("status"))) {
                throw new ServiceException(
                        ErrorStatuses.CONFLICT,
                        "Tour is not open for booking");
            }

            Integer availableSeats = (Integer) tour.get("availableSeats");

            BigDecimal price = (BigDecimal) tour.get("price");

            if (availableSeats == null || price == null) {
                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Tour configuration is incomplete");
            }

            // 4. Check available seats
            if (participants > availableSeats) {
                throw new ServiceException(
                        ErrorStatuses.CONFLICT,
                        "Not enough available seats");
            }

            // 5. Calculate total price
            BigDecimal totalPrice = price.multiply(
                    BigDecimal.valueOf(participants));

            booking.setTotalPrice(totalPrice);
            booking.setStatus("CONFIRMED");

            // 6. Reserve available seats
            db.run(
                    Update.entity("trekking.Tours")
                            .data(
                                    "availableSeats",
                                    availableSeats - participants)
                            .byId(tourId));
        }
    }

    @On(event = "cancelBooking")
    public String cancelBooking(EventContext ctx) {
        Object idValue = ctx.get("bookingId");
        if (idValue == null || !(idValue instanceof String)) {
            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Booking ID is required and must be a string");
        }

        String bookingId = (String) idValue;

        // 1. Find and lock booking
        var booking = db.run(
                Select.from("trekking.Bookings")
                        .byId(bookingId)
                        .lock())
                .first().orElseThrow(() -> new ServiceException(
                        ErrorStatuses.NOT_FOUND,
                        "Booking not found"));

        // 2. Check booking status
        if (!"CONFIRMED".equals(booking.get("status"))) {
            throw new ServiceException(
                    ErrorStatuses.CONFLICT,
                    "Booking is not in a state that can be cancelled");
        }

        // 3 Find and lock tour
        String tourId = (String) booking.get("tourId");
        var tour = db.run(
                Select.from("trekking.Tours")
                        .byId(tourId)
                        .lock())
                .first().orElseThrow(() -> new ServiceException(
                        ErrorStatuses.NOT_FOUND,
                        "Tour not found"));

        // 4. Update booking status to CANCELLED
        db.run(
                Update.entity("trekking.Bookings")
                        .data("status", "CANCELLED")
                        .byId(bookingId));

        // 5. Release reserved seats
        Integer seats = (Integer) tour.get("availableSeats");
        Integer participants = (Integer) booking.get("participants");
        db.run(
                Update.entity("trekking.Tours")
                        .data("availableSeats", seats + participants)
                        .byId(tourId));

        return "Booking cancelled successfully";
    }
}
