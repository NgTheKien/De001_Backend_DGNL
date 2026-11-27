# Bài làm CinemaBox – Database + Backend

Họ tên: Nguyễn Thế Kiên
Mã sinh viên:
Lớp:

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [x] F1  - [x] F2  - [x] F3  - [x] API khách hàng (tạo, xem, danh sách, validate, lỗi)

Ghi chú (nếu có):
- F2: điều kiện `status = 'CONFIRMED'` đặt trong `ON` của LEFT JOIN để giữ phim chưa bán vé (so_ve = 0, doanh_thu = 0).
- F3: `so_lan_dat` tính bằng `COUNT(b.id)` (số lần đặt CONFIRMED).
- API khách: memberLevel validate bằng `@Pattern` để sai giá trị vẫn ra 400 VALIDATION_FAILED có details. Thêm handler cho JSON hỏng, sai kiểu tham số, page/size ngoài phạm vi để không bị 500.

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

Em dùng 3 lớp:
1. **Khoá theo suất chiếu** (`ShowtimeLocks`, mỗi suất một `ReentrantLock`). Request cùng suất xếp hàng, khác suất vẫn chạy song song. Khoá bọc ngoài transaction, nhả ra sau khi commit/rollback xong, nên request sau luôn thấy đúng số ghế, kể cả khi request trước thanh toán lỗi rồi rollback. Nhờ vậy không từ chối nhầm.
2. **Trừ ghế nguyên tử**: `UPDATE showtimes SET available_seats = available_seats - :q WHERE id = :id AND available_seats >= :q`. Trả về 0 dòng là không đủ ghế, ném 409 NOT_ENOUGH_SEATS. Không có khe "đọc xong mới ghi".
3. **CHECK trong DB**: `available_seats >= 0` (schema.sql), để code có sai thì DB vẫn chặn được ghế âm.

Em không giữ khoá dòng của DB trong lúc gọi cổng thanh toán (80 ms), vì nhiều request xếp hàng dễ gặp lock timeout. Khoá trong JVM thì chờ không giới hạn và không chiếm connection.

Kiểm chứng: test `nhieuNguoiDatCungLuc...` (12 luồng, 5 ghế: đúng 5 vé 201, 7 lần 409, còn 0 ghế), `dongThoiHaiSuatChieu...`, `dongThoiLanThanhToanLoi...`.

### Cách bạn hoàn tác khi thanh toán lỗi

Trừ ghế, gọi `PaymentGateway.charge()` và lưu vé nằm chung trong một `@Transactional` (`BookingTxService.book`). Thứ tự là trừ ghế trước, thanh toán sau, rồi mới lưu vé. `charge()` ném `PaymentFailedException` (RuntimeException) thì Spring rollback cả transaction: số ghế vừa trừ được trả lại, chưa có dòng bookings nào. `GlobalExceptionHandler` đổi lỗi đó thành 402 PAYMENT_FAILED. Kiểm tra số ghế trước khi thanh toán nên không đủ ghế thì không bị trừ tiền.

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

Lớp `vn.rikkei.cinemabox.booking.BookingApiTest`. Mỗi test tự tạo khách và suất chiếu riêng, không dùng id cứng.

| Test | Kiểm tra |
| --- | --- |
| `datVeHopLe_tra201_truGhe_luuVeConfirmedCoPaymentRef` | 201 đúng body, trừ đúng ghế, vé CONFIRMED có paymentRef `PAY-...` |
| `khachGold_duocGiam10Phan_lamTronXuong` | GOLD: 99.999 × 3 giảm 10% = 269.997 (làm tròn xuống) |
| `datDungBangSoGheConLai_duoc_vuotThi409_vaKhongDoiDuLieu` | Đặt hết đúng 3/3 ghế được; đặt thêm thì 409 NOT_ENOUGH_SEATS, dữ liệu không đổi |
| `duLieuSai_tra400_khongDoiDuLieu` | quantity 0/11, body rỗng (3 lỗi details), JSON hỏng: 400; ghế không đổi |
| `khachHoacSuatChieuKhongTonTai_tra404` | Khách hoặc suất không có: 404 NOT_FOUND |
| `thanhToanBiTuChoi_tra402_gheGiuNguyen_khongCoVeConfirmed` | 1.200.000 > hạn mức: 402 PAYMENT_FAILED, ghế giữ nguyên, 0 vé CONFIRMED (hoàn tác) |
| `huyVe_traLaiGhe_huyLanHaiThi409_veKhongTonTaiThi404` | Huỷ: 200 CANCELLED, trả lại ghế; huỷ lại: 409 INVALID_STATE; vé không có: 404 |
| `nhieuNguoiDatCungLuc_banDungBangSoGhe_khongVuot` | 12 luồng vào suất 5 ghế: đúng 5 thành công, không vượt, ghế = 0 |
| `dongThoiLanThanhToanLoi_veHopLeVanDatDuoc_duLieuKhop` | 12 luồng, một nửa bị từ chối thanh toán: 6 × 201, 6 × 402, ghế ban đầu = còn lại + vé CONFIRMED |
| `dongThoiHaiSuatChieu_khongSuatNaoBanVuot` | 16 luồng chia 2 suất (3 và 4 ghế): mỗi suất bán đúng số ghế, số ghế khớp số vé |

Chạy: `mvn test` (hoặc Run All Tests trong IntelliJ).

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
| --- | --- | --- | --- |
| 1 | Nhờ AI làm Mục 4.3: API đặt vé, chống bán vượt ghế, hoàn tác khi thanh toán lỗi, huỷ vé | Khoá theo suất chiếu bọc ngoài transaction + UPDATE trừ ghế có điều kiện + CHECK `available_seats >= 0`. Trừ ghế, charge và lưu vé chung một `@Transactional` | **Giữ**. Em hiểu lý do không giữ khoá dòng DB lúc gọi thanh toán 80 ms (request chờ lâu dễ lock timeout). Kiểm chứng: chạy `BookingApiTest`, kết quả: ___ / 10 test xanh |
| 2 | Nhờ AI viết test cho tình huống khó | 10 test MockMvc, test đồng thời dùng ExecutorService + CountDownLatch, mỗi test tự tạo khách và suất chiếu | **Giữ**. AI tự sửa 3 chỗ trước khi đưa: so id bằng `intValue()` (JSON trả Integer), bỏ kiểm tra toàn bảng vì phụ thuộc dữ liệu mẫu, tăng lên 12 ghế ở test trộn thanh toán lỗi để luôn ra 402 chứ không lẫn 409 |
| 3 | Kiểm tra lại bằng tay | Gọi curl: đặt 2 vé hợp lệ, đặt 2 vé × giá > 500.000, huỷ vé 2 lần | Kết quả: 201 / 402 (ghế không đổi) / 200 rồi 409: ___ (điền sau khi chạy) |
