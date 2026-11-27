package vn.rikkei.cinemabox.common;

/** Suất chiếu không còn đủ ghế. GlobalExceptionHandler chuyển thành 409 NOT_ENOUGH_SEATS. */
public class NotEnoughSeatsException extends RuntimeException {

    public NotEnoughSeatsException(String message) {
        super(message);
    }
}
