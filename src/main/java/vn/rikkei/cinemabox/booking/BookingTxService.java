package vn.rikkei.cinemabox.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.InvalidStateException;
import vn.rikkei.cinemabox.common.NotEnoughSeatsException;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.payment.PaymentGateway;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

/**
 * Phần chạy trong transaction. Tách riêng khỏi BookingService để khoá suất chiếu
 * bọc ngoài transaction (khoá chỉ mở sau khi commit/rollback xong).
 */
@Service
public class BookingTxService {

    static final String CONFIRMED = "CONFIRMED";
    static final String CANCELLED = "CANCELLED";

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentGateway paymentGateway;

    public BookingTxService(BookingRepository bookingRepository, CustomerRepository customerRepository,
                            ShowtimeRepository showtimeRepository, PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Trừ ghế -> thanh toán -> lưu vé, tất cả trong một transaction.
     * charge() ném PaymentFailedException (RuntimeException) thì Spring rollback,
     * ghế vừa trừ được trả lại và không có dòng bookings nào được lưu.
     */
    @Transactional
    public BookingResponse book(CreateBookingRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + req.customerId()));
        Showtime showtime = showtimeRepository.findById(req.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + req.showtimeId()));

        int quantity = req.quantity();
        long total = showtime.getPrice() * quantity;
        if ("GOLD".equals(customer.getMemberLevel())) {
            total = total * 90 / 100; // giảm 10%, chia số nguyên = làm tròn xuống tới đồng
        }

        if (showtimeRepository.decreaseSeats(showtime.getId(), quantity) == 0) {
            throw new NotEnoughSeatsException("Suất chiếu id=" + showtime.getId() + " chỉ còn "
                    + showtime.getAvailableSeats() + " ghế, không đủ " + quantity + " ghế");
        }

        String paymentRef = paymentGateway.charge(customer.getId(), total);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setShowtime(showtime);
        booking.setQuantity(quantity);
        booking.setTotalAmount(total);
        booking.setStatus(CONFIRMED);
        booking.setPaymentRef(paymentRef);
        bookingRepository.save(booking);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public Long findShowtimeIdOfBooking(Long bookingId) {
        return bookingRepository.findShowtimeIdById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé id=" + bookingId));
    }

    @Transactional
    public BookingResponse cancel(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé id=" + bookingId));
        if (!CONFIRMED.equals(booking.getStatus())) {
            throw new InvalidStateException("Vé id=" + bookingId + " đang ở trạng thái " + booking.getStatus()
                    + ", chỉ huỷ được vé CONFIRMED");
        }
        booking.setStatus(CANCELLED);
        // flushAutomatically: ghi trạng thái CANCELLED xuống trước rồi mới cộng lại ghế
        showtimeRepository.increaseSeats(booking.getShowtime().getId(), booking.getQuantity());
        return BookingResponse.from(booking);
    }
}
