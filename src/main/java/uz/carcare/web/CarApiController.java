package uz.carcare.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.carcare.dto.Dtos.*;
import uz.carcare.model.ServiceType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Frontend ishlatadigan REST API. */
@RestController
@RequestMapping("/api")
public class CarApiController {

    private final CarService service;
    private final CustomerLoginService login;

    public CarApiController(CarService service, CustomerLoginService login) {
        this.service = service;
        this.login = login;
    }

    @GetMapping("/service-types")
    public List<ServiceTypeDto> serviceTypes() {
        return Arrays.stream(ServiceType.values())
                .map(t -> new ServiceTypeDto(t.name(), t.getLabel()))
                .toList();
    }

    @PostMapping("/cars")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        return service.register(req);
    }

    @GetMapping("/cars/{token}")
    public CarDto get(@PathVariable String token) {
        return service.get(token);
    }

    @PatchMapping("/cars/{token}/settings")
    public CarDto settings(@PathVariable String token, @RequestBody SettingsRequest req) {
        return service.updateSettings(token, req);
    }

    @PostMapping("/cars/{token}/records")
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto addRecord(@PathVariable String token, @Valid @RequestBody RecordRequest req) {
        return service.addRecord(token, req);
    }

    @DeleteMapping("/cars/{token}/records/{id}")
    public CarDto deleteRecord(@PathVariable String token, @PathVariable Long id) {
        return service.deleteRecord(token, id);
    }

    @PostMapping("/cars/{token}/test")
    public Map<String, Boolean> test(@PathVariable String token) {
        return Map.of("ok", service.sendTest(token));
    }

    /** Hozirgi probegni kiritish: km bo'yicha eslatmalar darhol tekshiriladi. */
    @PostMapping("/cars/{token}/mileage")
    public CarDto mileage(@PathVariable String token, @Valid @RequestBody MileageRequest req) {
        return service.updateMileage(token, req);
    }

    /** Havolani yangilash: javobda yangi token keladi, eski havola ishlamay qoladi. */
    @PostMapping("/cars/{token}/rotate")
    public CarDto rotate(@PathVariable String token) {
        return service.rotateLink(token);
    }

    /** Telegram orqali tasdiqlash holati (brauzer har 2 soniyada so'raydi). */
    @GetMapping("/login/{code}")
    public LoginStatusDto loginStatus(@PathVariable String code) {
        return login.poll(code);
    }
}
