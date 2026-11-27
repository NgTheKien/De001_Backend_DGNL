package vn.rikkei.cinemabox.booking;

import org.springframework.stereotype.Service;

/**
 * Không có @Transactional ở đây: khoá suất chiếu bọc ngoài, transaction nằm trong BookingTxService.
 * Thứ tự: lấy khoá -> mở transaction -> commit/rollback -> nhả khoá.
 */
@Service
public class BookingService {

    private final BookingTxService txService;
    private final ShowtimeLocks showtimeLocks;

    public BookingService(BookingTxService txService, ShowtimeLocks showtimeLocks) {
        this.txService = txService;
        this.showtimeLocks = showtimeLocks;
    }

    public BookingResponse book(CreateBookingRequest req) {
        return showtimeLocks.withLock(req.showtimeId(), () -> txService.book(req));
    }

    public BookingResponse cancel(Long bookingId) {
        Long showtimeId = txService.findShowtimeIdOfBooking(bookingId);
        return showtimeLocks.withLock(showtimeId, () -> txService.cancel(bookingId));
    }
}
