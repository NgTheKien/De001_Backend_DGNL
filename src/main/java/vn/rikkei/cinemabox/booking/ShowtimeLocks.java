package vn.rikkei.cinemabox.booking;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Mỗi suất chiếu một khoá. Request vào cùng suất chiếu xếp hàng lần lượt,
 * request khác suất chiếu vẫn chạy song song.
 * Khoá được giữ tới khi transaction đã commit hoặc rollback xong,
 * nên request sau luôn thấy đúng số ghế của request trước (kể cả khi request trước thanh toán lỗi).
 */
@Component
public class ShowtimeLocks {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(Long showtimeId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(showtimeId, id -> new ReentrantLock(true));
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
