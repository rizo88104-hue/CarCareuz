package uz.carcare.reminder;

import org.junit.jupiter.api.Test;
import uz.carcare.model.ServiceRecord;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Qarz eslatmasi qoidasi: ertaga — bir marta; muddat o'tgan — darhol, keyin har 7 kunda. */
class DebtReminderRuleTest {

    private static ServiceRecord rec(LocalDate due, LocalDate lastReminded) {
        ServiceRecord r = new ServiceRecord();
        r.setPayDueDate(due);
        r.setDebtRemindedOn(lastReminded);
        return r;
    }

    @Test
    void remindsDayBeforeOnce() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertThat(DebtBirthdayReminder.shouldRemind(rec(today.plusDays(1), null), today)).isTrue();
        assertThat(DebtBirthdayReminder.shouldRemind(rec(today.plusDays(1), today), today)).isFalse();
        assertThat(DebtBirthdayReminder.shouldRemind(rec(today.plusDays(3), null), today)).isFalse();
    }

    @Test
    void overdueRemindsImmediatelyThenWeekly() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        LocalDate due = today.minusDays(2);
        assertThat(DebtBirthdayReminder.shouldRemind(rec(due, null), today)).isTrue();
        assertThat(DebtBirthdayReminder.shouldRemind(rec(due, due.minusDays(1)), today)).isTrue();   // oxirgi eslatma muddatdan oldin edi («ertaga»)
        assertThat(DebtBirthdayReminder.shouldRemind(rec(due, due), today)).isFalse();               // muddat kuni eslatilgan, 7 kun o'tmagan
        assertThat(DebtBirthdayReminder.shouldRemind(rec(due, today.minusDays(7)), today)).isTrue();  // 7 kun o'tdi — yana
        assertThat(DebtBirthdayReminder.shouldRemind(rec(due, today), today)).isFalse();
    }
}
