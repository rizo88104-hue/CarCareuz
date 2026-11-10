package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Telefon + mashina raqami bilan qayta kirish so'rovi. Mashina Telegramga ulangan bo'lsa,
 * havola darhol berilmaydi: egasi botda «Ha, bu men» tugmasini bosgandan keyingina beriladi.
 */
@Entity
@Table(name = "login_requests", indexes = {
        @Index(name = "ix_login_code", columnList = "code", unique = true),
        @Index(name = "ix_login_car", columnList = "car_id")
})
public class LoginRequest {

    public enum Status { PENDING, APPROVED, DENIED, USED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Brauzer natijani shu kod bo'yicha so'rab turadi (URL da ishlatiladi). */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id")
    private Car car;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    protected LoginRequest() {
    }

    public LoginRequest(Car car, String code, Instant expiresAt) {
        this.car = car;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Car getCar() { return car; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
