package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Avtoservisning obuna (oylik abonement) to'lovi. Super admin qabul qiladi. */
@Entity
@Table(name = "subscription_payments", indexes = {
        @Index(name = "ix_subpay_center", columnList = "center_id")
})
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id")
    private ServiceCenter center;

    /** To'langan summa, so'm. */
    @Column(nullable = false)
    private long amount;

    /** Necha oy uchun. */
    @Column(nullable = false)
    private int months;

    /** Obuna qaysi davr uchun uzaytirildi. */
    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Column(length = 200)
    private String note;

    /** Kim qabul qildi (super admin ismi). */
    @Column(length = 80)
    private String recordedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SubscriptionPayment() {
    }

    public SubscriptionPayment(ServiceCenter center, long amount, int months, LocalDate periodFrom, LocalDate periodTo,
                               String note, String recordedBy) {
        this.center = center;
        this.amount = amount;
        this.months = months;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.note = note;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public ServiceCenter getCenter() { return center; }
    public long getAmount() { return amount; }
    public int getMonths() { return months; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public String getNote() { return note; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
