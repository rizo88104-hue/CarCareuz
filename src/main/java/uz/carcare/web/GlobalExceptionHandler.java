package uz.carcare.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.carcare.i18n.I18n;

import java.util.Map;

/** Barcha xatoliklarni {"error": "..."} ko'rinishida qaytaradi. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    /**
     * Validatsiya xabarlari DTO larda "{v.kalit}" ko'rinishida — bu yerda joriy tilga tarjima qilinadi.
     * Oddiy matn bo'lsa, o'zi qaytadi.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> resolve(f.getDefaultMessage()))
                .orElse(I18n.t("err.validation"));
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static String resolve(String msg) {
        if (msg != null && msg.length() > 2 && msg.startsWith("{") && msg.endsWith("}")) {
            return I18n.t(msg.substring(1, msg.length() - 1));
        }
        return msg == null ? I18n.t("err.validation") : msg;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", I18n.t("err.format")));
    }

    /** Ikki xodim bir vaqtda bir narsani o'zgartirdi (masalan, bir kartadan ball yechish). */
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflict(Exception e) {
        log.warn("Bir vaqtdagi o'zgarish: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", I18n.t("err.concurrent")));
    }

    /** Masalan, bir xil login yoki mashina raqami bir vaqtda ikki marta saqlanmoqchi bo'lsa. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(Exception e) {
        log.warn("Ma'lumotlar bazasi cheklovi: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", I18n.t("err.integrity")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        // Spring ning o'z xatolari (404 sahifa, noto'g'ri metod va h.k.) — o'z statusi bilan
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", I18n.t("err.requestFailed")));
        }
        log.error("Kutilmagan xato", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", I18n.t("err.server")));
    }
}
