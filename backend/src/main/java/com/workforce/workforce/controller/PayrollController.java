package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Attendance;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.LeaveRequest;
import com.workforce.workforce.entity.Payroll;
import com.workforce.workforce.repository.AttendanceRepository;
import com.workforce.workforce.repository.EmployeeRepository;
import com.workforce.workforce.repository.LeaveRequestRepository;
import com.workforce.workforce.repository.PayrollRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final int LATE_PER_ABSENCE = 3;

    private final PayrollRepository payroll;
    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final LeaveRequestRepository leaves;
    private final UserRepository users;

    public PayrollController(PayrollRepository payroll, EmployeeRepository employees,
                             AttendanceRepository attendance, LeaveRequestRepository leaves,
                             UserRepository users) {
        this.payroll = payroll;
        this.employees = employees;
        this.attendance = attendance;
        this.leaves = leaves;
        this.users = users;
    }

    public record GenerateForm(Integer month, Integer year, Boolean deductAbsences) {}

    public record BonusForm(BigDecimal bonus) {}

    private ResponseEntity<?> error(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("error", text));
    }

    private boolean isWeekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }

    private int weekdays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWeekday(d)) {
                n++;
            }
        }
        return n;
    }

    private void recalc(Payroll p) {
        BigDecimal taxable = p.getBaseSalary()
                .add(p.getBonus())
                .subtract(p.getAbsenceDeduction())
                .subtract(p.getLateDeduction());
        if (taxable.signum() < 0) {
            taxable = BigDecimal.ZERO;
        }
        BigDecimal tax = taxable.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        p.setTax(tax);
        p.setNetSalary(taxable.subtract(tax).setScale(2, RoundingMode.HALF_UP));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateForm form, @AuthenticationPrincipal Jwt jwt) {
        if (form.month() == null || form.year() == null || form.month() < 1 || form.month() > 12) {
            return error(400, "Pick a valid month and year");
        }
        YearMonth ym = YearMonth.of(form.year(), form.month());
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();
        if (start.isAfter(today)) {
            return error(400, "Cannot generate payroll for a future month");
        }
        LocalDate countEnd = end.isAfter(today) ? today : end;
        boolean deduct = form.deductAbsences() == null || form.deductAbsences();
        int workingDays = weekdays(start, end);

        List<Payroll> result = new ArrayList<>();
        for (Employee e : employees.findAll()) {
            if (!"ACTIVE".equals(e.getStatus()) || e.getBaseSalary() == null) {
                continue;
            }
            if (e.getHireDate() != null && e.getHireDate().isAfter(countEnd)) {
                continue;
            }

            // absences are only counted from the hire date onwards
            LocalDate from = (e.getHireDate() != null && e.getHireDate().isAfter(start))
                    ? e.getHireDate() : start;

            Set<LocalDate> present = new HashSet<>();
            int lates = 0;
            for (Attendance a : attendance.findByEmployeeIdAndWorkDateBetween(e.getId(), from, countEnd)) {
                if (isWeekday(a.getWorkDate())) {
                    present.add(a.getWorkDate());
                }
                if ("LATE".equals(a.getStatus())) {
                    lates++;
                }
            }

            Set<LocalDate> onLeave = new HashSet<>();
            for (LeaveRequest r : leaves.findByEmployeeIdAndStatus(e.getId(), "APPROVED")) {
                LocalDate d = r.getStartDate().isAfter(from) ? r.getStartDate() : from;
                LocalDate last = r.getEndDate().isBefore(countEnd) ? r.getEndDate() : countEnd;
                while (!d.isAfter(last)) {
                    if (isWeekday(d) && !present.contains(d)) {
                        onLeave.add(d);
                    }
                    d = d.plusDays(1);
                }
            }

            int expected = weekdays(from, countEnd);
            int absent = Math.max(0, expected - present.size() - onLeave.size());

            BigDecimal base = e.getBaseSalary();
            BigDecimal daily = base.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);

            Payroll p = payroll.findByEmployeeIdAndMonthAndYear(e.getId(), form.month(), form.year())
                    .orElseGet(Payroll::new);
            p.setEmployee(e);
            p.setMonth(form.month());
            p.setYear(form.year());
            p.setBaseSalary(base);
            p.setWorkingDays(workingDays);
            p.setPresentDays(present.size());
            p.setLeaveDays(onLeave.size());
            p.setAbsentDays(absent);
            p.setLateCount(lates);
            p.setAbsenceDeduction(deduct ? daily.multiply(BigDecimal.valueOf(absent)) : BigDecimal.ZERO);
            p.setLateDeduction(deduct
                    ? daily.multiply(BigDecimal.valueOf(lates / LATE_PER_ABSENCE)) : BigDecimal.ZERO);
            p.setGeneratedAt(LocalDateTime.now());
            p.setGeneratedBy(jwt.getSubject());
            recalc(p);
            result.add(payroll.save(p));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public List<Payroll> forMonth(@RequestParam int month, @RequestParam int year) {
        return payroll.findByYearAndMonthOrderByEmployeeFirstNameAsc(year, month);
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = users.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        return ResponseEntity.ok(payroll.findByEmployeeIdOrderByYearDescMonthDesc(emp.getId()));
    }

    @PutMapping("/{id}/bonus")
    public ResponseEntity<?> setBonus(@PathVariable Long id, @RequestBody BonusForm form) {
        Payroll p = payroll.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        if (form.bonus() == null || form.bonus().signum() < 0) {
            return error(400, "Bonus must be zero or more");
        }
        p.setBonus(form.bonus());
        recalc(p);
        return ResponseEntity.ok(payroll.save(p));
    }
}
