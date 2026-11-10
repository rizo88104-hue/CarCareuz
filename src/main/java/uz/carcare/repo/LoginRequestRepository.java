package uz.carcare.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.carcare.model.Car;
import uz.carcare.model.LoginRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoginRequestRepository extends JpaRepository<LoginRequest, Long> {

    Optional<LoginRequest> findByCode(String code);

    long countByCarAndCreatedAtAfter(Car car, Instant after);

    List<LoginRequest> findAllByCarAndStatus(Car car, LoginRequest.Status status);

    @Modifying
    @Query("delete from LoginRequest r where r.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
