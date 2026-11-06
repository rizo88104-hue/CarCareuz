package uz.carcare.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.carcare.model.Car;

import java.util.List;
import java.util.Optional;

public interface CarRepository extends JpaRepository<Car, Long> {

    Optional<Car> findByToken(String token);

    Optional<Car> findByPlateNumber(String plateNumber);

    List<Car> findAllByTelegramChatId(Long telegramChatId);
}
