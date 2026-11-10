package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Super admin uchun ikki bosqichli tasdiq: parol to'g'ri kiritilgach Telegram'ga yuborilgan
 * 6 xonali kod. Kodning o'zi saqlanmaydi — faqat SHA-256 xeshi.
 */
@Entity
@Table(name = "admin_login_codes", indexes = {
        @Index(name = "ix_alc_challenge", columnList = "challenge", unique = true)
})
public class AdminLoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Brauzer kodni shu identifikator bilan birga yuboradi (parol o'rniga). */
    @Column(nullable = false, unique = true, length = 48)
    private String challenge;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private StaffUser staff;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean used = false;

    /** Kirishga urinilgan IP (Telegram xabarida ko'rsatiladi, jurnalga yoziladi). */
    @Column(length = 45)
    private String ip;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    protected AdminLoginCode() {
    }

    public AdminLoginCode(StaffUser staff, String challenge, String codeHash, String ip, Instant expiresAt) {
        this.staff = staff;
        this.challenge = challenge;
        this.codeHash = codeHash;
        this.ip = ip;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public String getChallenge() { return challenge; }
    public StaffUser getStaff() { return staff; }
    public String getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public String getIp() { return ip; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
