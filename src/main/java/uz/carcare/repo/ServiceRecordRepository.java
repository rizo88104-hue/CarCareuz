package uz.carcare.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.carcare.model.Car;
import uz.carcare.model.ServiceCenter;
import uz.carcare.model.ServiceRecord;
import uz.carcare.model.ServiceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {

    List<ServiceRecord> findAllByCarOrderByActiveDescNextDateAscIdDesc(Car car);

    List<ServiceRecord> findAllByCarAndActiveTrueOrderByNextDateAsc(Car car);

    List<ServiceRecord> findAllByCarAndTypeAndActiveTrue(Car car, ServiceType type);

    Optional<ServiceRecord> findByIdAndCar(Long id, Car car);

    /**
     * Eslatma yuborilishi kerak bo'lgan yozuvlar:
     *  - muddatiga 7 kun (yoki kamroq) qolgan va haftalik eslatma hali ketmagan,
     *  - yoki muddati kelgan va "bugun" eslatmasi hali ketmagan.
     */
    /** Bitta avtoservis kiritgan barcha yozuvlar (statistika va ro'yxatlar uchun). */
    @Query("""
            select r from ServiceRecord r join fetch r.car
            left join fetch r.replacedByCenter
            where r.center = :center
            order by r.createdAt desc
            """)
    List<ServiceRecord> findAllForCenter(@Param("center") ServiceCenter center);

    @Query("""
            select r from ServiceRecord r join fetch r.car left join fetch r.center
            where r.active = true and (
                 (r.weekReminderSent = false and r.nextDate <= :weekLimit)
              or (r.dueReminderSent = false and r.nextDate <= :today)
            )
            """)
    List<ServiceRecord> findPendingReminders(@Param("today") LocalDate today,
                                             @Param("weekLimit") LocalDate weekLimit);
}
