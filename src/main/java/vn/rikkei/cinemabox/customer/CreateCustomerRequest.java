package vn.rikkei.cinemabox.customer;

import jakarta.validation.constraints. Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body của POST /api/customers. */
public record CreateCustomerRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        // null được coi là hợp lệ với @Pattern -> phone không bắt buộc
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại gồm đúng 10 chữ số, bắt đầu bằng 0")
        String phone,

        // null hợp lệ -> Customer.prePersist() tự đặt STANDARD
        @Pattern(regexp = "^(STANDARD|GOLD)$", message = "memberLevel chỉ nhận STANDARD hoặc GOLD")
        String memberLevel) {
}