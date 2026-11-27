package vn.rikkei.cinemabox.showtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    /**
     * Trừ ghế nguyên tử: chỉ trừ khi còn đủ. Trả về số dòng bị sửa (0 = không đủ ghế).
     * Kiểm tra và trừ nằm trong một câu UPDATE nên không có khe hở "đọc rồi mới ghi".
     */
    @Modifying(flushAutomatically = true)
    @Query("update Showtime s set s.availableSeats = s.availableSeats - :quantity "
            + "where s.id = :id and s.availableSeats >= :quantity")
    int decreaseSeats(@Param("id") Long id, @Param("quantity") int quantity);

    /** Trả lại ghế khi huỷ vé. */
    @Modifying(flushAutomatically = true)
    @Query("update Showtime s set s.availableSeats = s.availableSeats + :quantity where s.id = :id")
    int increaseSeats(@Param("id") Long id, @Param("quantity") int quantity);
}
