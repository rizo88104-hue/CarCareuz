package uz.carcare.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.carcare.model.ServiceCenter;
import uz.carcare.model.SubscriptionPayment;

import java.util.List;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findAllByCenterOrderByCreatedAtDesc(ServiceCenter center);
}
