package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Avtoservis (super admin qo'shadi). */
@Entity
@Table(name = "service_centers")
public class ServiceCenter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    /** Format: 998901234567 */
    @Column(length = 12)
    private String phone;

    /** O'chirilgan servis tizimga kira olmaydi. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Obuna (oylik to'lov) qaysi kungacha to'langan. Shu kun + imtiyoz kunlari o'tgach servis xodimlari
     * kira olmaydi. null — hali belgilanmagan (ishga tushganda sinov muddati beriladi).
     */
    private LocalDate subscriptionUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDate getSubscriptionUntil() { return subscriptionUntil; }
    public void setSubscriptionUntil(LocalDate subscriptionUntil) { this.subscriptionUntil = subscriptionUntil; }
    public Instant getCreatedAt() { return createdAt; }
}
