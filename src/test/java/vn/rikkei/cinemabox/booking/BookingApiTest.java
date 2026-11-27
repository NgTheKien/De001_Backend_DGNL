package vn.rikkei.cinemabox.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.movie.MovieRepository;
import vn.rikkei.cinemabox.showtime.RoomRepository;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

/**
 * Test API đặt vé. Mỗi test tự tạo khách và suất chiếu riêng, không phụ thuộc id của dữ liệu mẫu.
 * Không đặt @Transactional ở lớp test: cần dữ liệu commit thật để kiểm tra rollback và đồng thời.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private RoomRepository roomRepository;

    // ---------- 4.3.1: luồng cơ bản ----------

    @Test
    void datVeHopLe_tra201_truGhe_luuVeConfirmedCoPaymentRef() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(80_000L, 10);

        mvc.perform(datVe(customerId, showtimeId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.intValue()))
                .andExpect(jsonPath("$.showtimeId").value(showtimeId.intValue()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(160_000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").value(org.hamcrest.Matchers.startsWith("PAY-")))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(gheConLai(showtimeId)).isEqualTo(8);
        assertThat(soVeConfirmed(showtimeId)).isEqualTo(1);
    }

    @Test
    void khachGold_duocGiam10Phan_lamTronXuong() throws Exception {
        Long customerId = taoKhach("GOLD");
        Long showtimeId = taoSuatChieu(99_999L, 10);

        // 99.999 x 3 = 299.997; giảm 10% = 269.997,3 -> 269.997
        mvc.perform(datVe(customerId, showtimeId, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(269_997));
    }

    @Test
    void datDungBangSoGheConLai_duoc_vuotThi409_vaKhongDoiDuLieu() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(50_000L, 3);

        mvc.perform(datVe(customerId, showtimeId, 3)).andExpect(status().isCreated());
        assertThat(gheConLai(showtimeId)).isZero();

        mvc.perform(datVe(customerId, showtimeId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SEATS"));
        assertThat(gheConLai(showtimeId)).isZero();
        assertThat(soVeConfirmed(showtimeId)).isEqualTo(1);
    }

    @Test
    void duLieuSai_tra400_khongDoiDuLieu() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(50_000L, 10);

        mvc.perform(datVe(customerId, showtimeId, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
        mvc.perform(datVe(customerId, showtimeId, 11))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(3));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        assertThat(gheConLai(showtimeId)).isEqualTo(10);
        assertThat(soVeConfirmed(showtimeId)).isZero();
    }

    @Test
    void khachHoacSuatChieuKhongTonTai_tra404() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(50_000L, 10);

        mvc.perform(datVe(Long.MAX_VALUE, showtimeId, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(datVe(customerId, Long.MAX_VALUE, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        assertThat(gheConLai(showtimeId)).isEqualTo(10);
    }

    // ---------- 4.3.3: thanh toán lỗi phải hoàn tác ----------

    @Test
    void thanhToanBiTuChoi_tra402_gheGiuNguyen_khongCoVeConfirmed() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(600_000L, 10);

        // 600.000 x 2 = 1.200.000 > hạn mức 1.000.000 -> FakePaymentGateway từ chối
        mvc.perform(datVe(customerId, showtimeId, 2))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));

        assertThat(gheConLai(showtimeId)).isEqualTo(10);
        assertThat(soVeConfirmed(showtimeId)).isZero();
    }

    // ---------- 4.3.4a: huỷ vé ----------

    @Test
    void huyVe_traLaiGhe_huyLanHaiThi409_veKhongTonTaiThi404() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(50_000L, 10);

        String body = mvc.perform(datVe(customerId, showtimeId, 4))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int bookingId = Integer.parseInt(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        assertThat(gheConLai(showtimeId)).isEqualTo(6);

        mvc.perform(post("/api/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.quantity").value(4));
        assertThat(gheConLai(showtimeId)).isEqualTo(10);

        mvc.perform(post("/api/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        assertThat(gheConLai(showtimeId)).isEqualTo(10);

        mvc.perform(post("/api/bookings/" + Long.MAX_VALUE + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ---------- 4.3.4c, d: nhiều request cùng lúc ----------

    @Test
    void nhieuNguoiDatCungLuc_banDungBangSoGhe_khongVuot() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(50_000L, 5);

        List<Integer> codes = chayDongThoi(12, i -> datVe(customerId, showtimeId, 1));

        assertThat(codes).filteredOn(c -> c == 201).hasSize(5);
        assertThat(codes).filteredOn(c -> c == 409).hasSize(7);
        assertThat(gheConLai(showtimeId)).isZero();
        assertThat(soVeConfirmed(showtimeId)).isEqualTo(5);
    }

    @Test
    void dongThoiLanThanhToanLoi_veHopLeVanDatDuoc_duLieuKhop() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long showtimeId = taoSuatChieu(600_000L, 12);

        // i chẵn: 1 vé = 600.000 (thanh toán được); i lẻ: 2 vé = 1.200.000 (bị từ chối)
        List<Integer> codes = chayDongThoi(12, i -> datVe(customerId, showtimeId, i % 2 == 0 ? 1 : 2));

        assertThat(codes).filteredOn(c -> c == 201).hasSize(6);
        assertThat(codes).filteredOn(c -> c == 402).hasSize(6);
        // ghế ban đầu = ghế còn lại + số vé CONFIRMED đã bán
        assertThat(gheConLai(showtimeId)).isEqualTo(12 - 6);
        assertThat(soVeConfirmed(showtimeId)).isEqualTo(6);
    }

    @Test
    void dongThoiHaiSuatChieu_khongSuatNaoBanVuot() throws Exception {
        Long customerId = taoKhach("STANDARD");
        Long suatA = taoSuatChieu(50_000L, 3);
        Long suatB = taoSuatChieu(50_000L, 4);

        List<Integer> codes = chayDongThoi(16, i -> datVe(customerId, i % 2 == 0 ? suatA : suatB, 1));

        assertThat(codes).filteredOn(c -> c == 201).hasSize(7);
        assertThat(gheConLai(suatA)).isZero();
        assertThat(gheConLai(suatB)).isZero();
        assertThat(soVeConfirmed(suatA)).isEqualTo(3);
        assertThat(soVeConfirmed(suatB)).isEqualTo(4);
    }

    // ---------- hàm phụ ----------

    private org.springframework.test.web.servlet.RequestBuilder datVe(Long customerId, Long showtimeId, int quantity) {
        String json = "{\"customerId\":" + customerId + ",\"showtimeId\":" + showtimeId + ",\"quantity\":" + quantity + "}";
        return post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private interface RequestFactory {
        org.springframework.test.web.servlet.RequestBuilder build(int i);
    }

    /** Bắn n request gần như cùng một thời điểm, trả về danh sách mã HTTP. */
    private List<Integer> chayDongThoi(int n, RequestFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return mvc.perform(factory.build(idx)).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : futures) {
                codes.add(f.get(30, TimeUnit.SECONDS));
            }
            return codes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long taoKhach(String memberLevel) {
        Customer c = new Customer();
        c.setFullName("Khách test");
        c.setEmail("test-" + UUID.randomUUID() + "@example.com");
        c.setMemberLevel(memberLevel);
        return customerRepository.save(c).getId();
    }

    private Long taoSuatChieu(long price, int seats) {
        Showtime s = new Showtime();
        s.setMovie(movieRepository.findAll().get(0));
        s.setRoom(roomRepository.findAll().get(0));
        s.setStartTime(LocalDateTime.now().plusDays(7).withNano(0));
        s.setPrice(price);
        s.setAvailableSeats(seats);
        return showtimeRepository.save(s).getId();
    }

    private int gheConLai(Long showtimeId) {
        return showtimeRepository.findById(showtimeId).orElseThrow().getAvailableSeats();
    }

    private long soVeConfirmed(Long showtimeId) {
        return bookingRepository.countByShowtime_IdAndStatus(showtimeId, "CONFIRMED");
    }
}
