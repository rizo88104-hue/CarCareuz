package uz.carcare.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.carcare.model.BotMessage;
import uz.carcare.model.Car;
import uz.carcare.repo.BotMessageRepository;
import uz.carcare.repo.CarRepository;

import java.util.List;

/**
 * Chiqqan xabarlarni jurnalga yozadi. Alohida tranzaksiyada: xabar ketgan bo'lsa,
 * chaqiruvchi amal bekor bo'lsa ham yozuv qoladi. Jurnalga yozishdagi xato xabar yuborishni buzmaydi.
 */
@Service
public class BotMessageLog {

    private static final Logger log = LoggerFactory.getLogger(BotMessageLog.class);

    private final BotMessageRepository messages;
    private final CarRepository cars;

    public BotMessageLog(BotMessageRepository messages, CarRepository cars) {
        this.messages = messages;
        this.cars = cars;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long chatId, String text, boolean delivered) {
        try {
            List<Car> list = cars.findAllByTelegramChatId(chatId);
            Car c = list.isEmpty() ? null : list.get(0);
            messages.save(new BotMessage(chatId, c == null ? null : c.getFullName().trim(),
                    c == null ? null : c.getPlateNumber(), text, delivered));
        } catch (Exception e) {
            log.warn("Bot xabarini jurnalga yozib bo'lmadi: {}", e.getMessage());
        }
    }
}
