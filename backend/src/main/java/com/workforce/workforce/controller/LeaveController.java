package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.LeaveRequest;
import com.workforce.workforce.entity.LeaveType;
import com.workforce.workforce.repository.LeaveRequestRepository;
import com.workforce.workforce.repository.LeaveTypeRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LeaveController {

    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final UserRepository users;

    public LeaveController(LeaveRequestRepository requests, LeaveTypeRepository types, UserRepository users) {
        this.requests = requests;
        this.types = types;
        this.users = users;
    }

    public record LeaveForm(Long leaveTypeId, LocalDate startDate, LocalDate endDate, String reason) {}

    public record BalanceRow(Long leaveTypeId, String leaveType, int allowed, int used, int remaining) {}

    private Employee currentEmployee(Jwt jwt) {
        return users.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
    }

    private ResponseEntity<?> error(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("error", text));
    }

    private int countWeekdays(LocalDate start, LocalDate end) {
        int n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY) {
                n++;
            }
        }
        return n;
    }

    private int usedDays(Long employeeId, Long leaveTypeId) {
        int year = LocalDate.now().getYear();
        return requests.findByEmployeeIdAndStatus(employeeId, "APPROVED").stream()
                .filter(r -> r.getLeaveType().getId().equals(leaveTypeId))
                .filter(r -> r.getStartDate().getYear() == year)
                .mapToInt(LeaveRequest::getDays)
                .sum();
    }

    @GetMapping("/leave-types")
    public List<LeaveType> leaveTypes() {
        return types.findAll();
    }

    @GetMapping("/leaves/balance")
    public ResponseEntity<?> balance(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        List<BalanceRow> rows = types.findAll().stream().map(t -> {
            int used = usedDays(emp.getId(), t.getId());
            return new BalanceRow(t.getId(), t.getName(), t.getDaysPerYear(), used, t.getDaysPerYear() - used);
        }).toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/leaves")
    public ResponseEntity<?> apply(@RequestBody LeaveForm form, @AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        if (form.leaveTypeId() == null || form.startDate() == null || form.endDate() == null) {
            return error(400, "Leave type, start date and end date are required");
        }
        if (form.endDate().isBefore(form.startDate())) {
            return error(400, "End date cannot be before start date");
        }
        LeaveType type = types.findById(form.leaveTypeId()).orElse(null);
        if (type == null) {
            return error(400, "Unknown leave type");
        }

        int days = countWeekdays(form.startDate(), form.endDate());
        if (days == 0) {
            return error(400, "Those dates are only weekend days");
        }
        int remaining = type.getDaysPerYear() - usedDays(emp.getId(), type.getId());
        if (days > remaining) {
            return error(400, "Not enough balance: you have " + remaining + " day(s) left for " + type.getName());
        }

        LeaveRequest r = new LeaveRequest();
        r.setEmployee(emp);
        r.setLeaveType(type);
        r.setStartDate(form.startDate());
        r.setEndDate(form.endDate());
        r.setDays(days);
        r.setReason(form.reason());
        r.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(requests.save(r));
    }

    @GetMapping("/leaves/my")
    public ResponseEntity<?> mine(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        return ResponseEntity.ok(requests.findByEmployeeIdOrderByCreatedAtDesc(emp.getId()));
    }

    @GetMapping("/leaves")
    public List<LeaveRequest> all() {
        return requests.findAllByOrderByCreatedAtDesc();
    }

    @PutMapping("/leaves/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return decide(id, jwt, true);
    }

    @PutMapping("/leaves/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return decide(id, jwt, false);
    }

    private ResponseEntity<?> decide(Long id, Jwt jwt, boolean approve) {
        LeaveRequest r = requests.findById(id).orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"PENDING".equals(r.getStatus())) {
            return error(409, "This request was already " + r.getStatus().toLowerCase());
        }

        Employee me = currentEmployee(jwt);
        if (me != null && me.getId().equals(r.getEmployee().getId())) {
            return error(403, "You cannot approve or reject your own leave");
        }

        if (approve) {
            int remaining = r.getLeaveType().getDaysPerYear()
                    - usedDays(r.getEmployee().getId(), r.getLeaveType().getId());
            if (r.getDays() > remaining) {
                return error(400, "The employee no longer has enough balance");
            }
        }

        r.setStatus(approve ? "APPROVED" : "REJECTED");
        r.setDecidedBy(jwt.getSubject());
        r.setDecidedAt(LocalDateTime.now());
        return ResponseEntity.ok(requests.save(r));
    }
}
