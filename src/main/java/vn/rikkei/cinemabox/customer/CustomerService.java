package vn.rikkei.cinemabox.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.BadRequestException;
import vn.rikkei.cinemabox.common.ConflictException;
import vn.rikkei.cinemabox.common.PageResponse;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        if (customerRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email đã tồn tại: " + req.email());
        }
        Customer c = new Customer();
        c.setFullName(req.fullName());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setMemberLevel(req.memberLevel()); // null -> prePersist đặt STANDARD
        return CustomerResponse.from(customerRepository.save(c));
    }

    public CustomerResponse getById(Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
        return CustomerResponse.from(c);
    }

    public PageResponse<CustomerResponse> list(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page phải lớn hơn hoặc bằng 0");
        }
        if (size < 1 || size > 50) {
            throw new BadRequestException("size phải từ 1 đến 50");
        }
        Page<CustomerResponse> p = customerRepository
                .findAll(PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(CustomerResponse::from);
        return PageResponse.from(p);
    }
}