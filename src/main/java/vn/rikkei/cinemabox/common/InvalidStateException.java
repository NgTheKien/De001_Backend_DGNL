package vn.rikkei.cinemabox.common;

/** Thao tác không hợp lệ với trạng thái hiện tại (huỷ vé đã huỷ). GlobalExceptionHandler chuyển thành 409 INVALID_STATE. */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
