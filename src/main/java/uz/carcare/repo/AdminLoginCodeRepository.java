package uz.carcare.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.carcare.model.AdminLoginCode;
import uz.carcare.model.StaffUser;

import java.time.Instant;
import java.util.Optional;

public interface AdminLoginCodeRepository extends JpaRepository<AdminLoginCode, Long> {

    Optional<AdminLoginCode> findByChallenge(String challenge);

    long countByStaffAndCreatedAtAfter(StaffUser staff, Instant after);

    @Modifying
    @Query("delete from AdminLoginCode c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
