package uz.carcare.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.carcare.model.Car;

import java.util.List;
import java.util.Optional;

public interface CarRepository extends JpaRepository<Car, Long> {

    Optional<Car> findByToken(String token);

    Optional<Car> findByPlateNumber(String plateNumber);

    List<Car> findAllByTelegramChatId(Long telegramChatId);

    long countByTelegramChatIdIsNotNull();

    /**
     * Botda "probegingiz qancha?" deb so'rash uchun: Telegram ulangan, keyingi probegi belgilangan faol
     * servisi bor, probeg :staleBefore dan beri yangilanmagan va :askedBefore dan beri so'ralmagan mashinalar.
     */
    @Query("""
            select distinct c from Car c
            where c.telegramChatId is not null
              and exists (select r from ServiceRecord r where r.car = c and r.active = true and r.nextMileage is not null)
              and (c.mileageUpdatedAt is null or c.mileageUpdatedAt < :staleBefore)
              and (c.mileageAskedOn is null or c.mileageAskedOn < :askedBefore)
            """)
    List<Car> findNeedingMileagePrompt(@Param("staleBefore") java.time.Instant staleBefore,
                                       @Param("askedBefore") java.time.LocalDate askedBefore);

    /** Tug'ilgan kun tabrigi uchun: tug'ilgan kuni kiritilgan va botga ulangan mijozlar. */
    @Query("select c from Car c where c.birthDate is not null and c.telegramChatId is not null")
    List<Car> findAllWithBirthdayAndTelegram();

    @Query("select c from Car c order by c.createdAt desc")
    List<Car> findLatest(Pageable page);

    /**
     * Super admin qidiruvi: mashina raqami, ism yoki telefon bo'yicha.
     * plate — katta harflarda, name — kichik harflarda, phone — faqat raqamlar (hammasi %...% bilan).
     */
    @Query("""
            select c from Car c
            where c.plateNumber like :plate
               or lower(concat(c.firstName, ' ', c.lastName)) like :name
               or c.phone like :phone
            order by c.createdAt desc
            """)
    List<Car> search(@Param("plate") String plate, @Param("name") String name, @Param("phone") String phone, Pageable page);
}
