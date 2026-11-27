package vn.rikkei.cinemabox.booking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Lấy id suất chiếu của vé, dùng để khoá đúng suất chiếu trước khi huỷ. */
    @Query("select b.showtime.id from Booking b where b.id = :id")
    Optional<Long> findShowtimeIdById(@Param("id") Long id);

    long countByShowtime_IdAndStatus(Long showtimeId, String status);
}
