package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Bitta servis yozuvi: masalan "Motor moyi 04.11.2026 da almashtirildi,
 * keyingisi 04.02.2027 da". Bir turdagi yangi yozuv qo'shilsa eskisi
 * arxivga o'tadi (active=false) va tarixda ko'rinadi.
 */
@Entity
@Table(name = "service_records", indexes = {
        @Index(name = "ix_records_next", columnList = "active,nextDate")
})
public class ServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_id")
    private Car car;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceType type;

    /** type=OTHER bo'lganda foydalanuvchi yozgan nom. */
    @Column(length = 80)
    private String customName;

    /** Qachon almashtirildi. */
    @Column(nullable = false)
    private LocalDate doneDate;

    /** Keyingi safar qachon almashtirish kerak (egasining o'zi belgilaydi). */
    @Column(nullable = false)
    private LocalDate nextDate;

    /** Almashtirilgan paytdagi probeg (ixtiyoriy). */
    private Integer mileage;

    /** Keyingi almashtirish probegi (ixtiyoriy). */
    private Integer nextMileage;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    /** "1 haftadan keyin" eslatmasi yuborildimi. */
    @Column(nullable = false)
    private boolean weekReminderSent = false;

    /** "Bugun almashtirish kuni" eslatmasi yuborildimi. */
    @Column(nullable = false)
    private boolean dueReminderSent = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Qaysi avtoservis kiritgan (null — mashina egasining o'zi kiritgan). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id")
    private ServiceCenter center;

    /** Shu turdagi keyingi servis qachon qilingan (null — hali qilinmagan). */
    private Instant replacedAt;

    /** Keyingi servisni qaysi avtoservis qilgan (null + replacedAt bor — egasi o'zi kiritgan). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_center_id")
    private ServiceCenter replacedByCenter;

    /** "Tayyor, keling" xabari qachon yuborilgan. */
    private Instant readyNotifiedAt;

    /** Ishni qaysi usta qilgan (servis kiritganda). */
    @Column(length = 80)
    private String masterName;

    public String getMasterName() { return masterName; }
    public void setMasterName(String masterName) { this.masterName = masterName; }

    /** Yozuvni arxivga o'tkazadi: shu turdagi yangi servis qilindi. */
    public void markReplaced(ServiceCenter byCenter) {
        this.active = false;
        this.replacedAt = Instant.now();
        this.replacedByCenter = byCenter;
    }

    public ServiceCenter getCenter() { return center; }
    public void setCenter(ServiceCenter center) { this.center = center; }
    public Instant getReplacedAt() { return replacedAt; }
    public ServiceCenter getReplacedByCenter() { return replacedByCenter; }
    public Instant getReadyNotifiedAt() { return readyNotifiedAt; }
    public void setReadyNotifiedAt(Instant readyNotifiedAt) { this.readyNotifiedAt = readyNotifiedAt; }

    public String displayName() {
        if (type == ServiceType.OTHER && customName != null && !customName.isBlank()) {
            return customName;
        }
        return type.getLabel();
    }

    public Long getId() { return id; }
    public Car getCar() { return car; }
    public void setCar(Car car) { this.car = car; }
    public ServiceType getType() { return type; }
    public void setType(ServiceType type) { this.type = type; }
    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public LocalDate getDoneDate() { return doneDate; }
    public void setDoneDate(LocalDate doneDate) { this.doneDate = doneDate; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public Integer getMileage() { return mileage; }
    public void setMileage(Integer mileage) { this.mileage = mileage; }
    public Integer getNextMileage() { return nextMileage; }
    public void setNextMileage(Integer nextMileage) { this.nextMileage = nextMileage; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isWeekReminderSent() { return weekReminderSent; }
    public void setWeekReminderSent(boolean weekReminderSent) { this.weekReminderSent = weekReminderSent; }
    public boolean isDueReminderSent() { return dueReminderSent; }
    public void setDueReminderSent(boolean dueReminderSent) { this.dueReminderSent = dueReminderSent; }
    public Instant getCreatedAt() { return createdAt; }
}
