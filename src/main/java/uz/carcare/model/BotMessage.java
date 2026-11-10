package uz.carcare.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Bot yuborgan (yoki yubormoqchi bo'lgan) har bir xabar — mijozga nima ketganini
 * tekshirish uchun. O'zgartirilmaydi.
 */
@Entity
@Table(name = "bot_messages", indexes = @Index(name = "ix_botmsg_created", columnList = "createdAt"))
public class BotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long chatId;

    /** Qabul qiluvchi (mashina egasi), topilsa. */
    @Column(length = 130)
    private String recipient;

    @Column(length = 16)
    private String plateNumber;

    @Column(nullable = false, length = 4096)
    private String text;

    /** Telegram qabul qildimi. */
    @Column(nullable = false)
    private boolean delivered;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BotMessage() {
    }

    public BotMessage(long chatId, String recipient, String plateNumber, String text, boolean delivered) {
        this.chatId = chatId;
        this.recipient = recipient;
        this.plateNumber = plateNumber;
        this.text = text.length() > 4096 ? text.substring(0, 4096) : text;
        this.delivered = delivered;
    }

    public Long getId() { return id; }
    public long getChatId() { return chatId; }
    public String getRecipient() { return recipient; }
    public String getPlateNumber() { return plateNumber; }
    public String getText() { return text; }
    public boolean isDelivered() { return delivered; }
    public Instant getCreatedAt() { return createdAt; }
}
