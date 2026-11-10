package uz.carcare.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.carcare.model.BotMessage;

import java.util.List;

public interface BotMessageRepository extends JpaRepository<BotMessage, Long> {

    List<BotMessage> findAllByOrderByIdDesc(Pageable page);
}
