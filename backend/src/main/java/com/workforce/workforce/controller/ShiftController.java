package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.LeaveRequest;
import com.workforce.workforce.entity.Shift;
import com.workforce.workforce.entity.ShiftAssignment;
import com.workforce.workforce.repository.EmployeeRepository;
import com.workforce.workforce.repository.LeaveRequestRepository;
import com.workforce.workforce.repository.ShiftAssignmentRepository;
import com.workforce.workforce.repository.ShiftRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final ShiftRepository shifts;
    private final ShiftAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaves;
    private final UserRepository users;
    private final RestClient ai;

    public ShiftController(ShiftRepository shifts, ShiftAssignmentRepository assignments,
                           EmployeeRepository employees, LeaveRequestRepository leaves,
                           UserRepository users,
                           @Value("${ai.service.url:http://localhost:8000}") String aiUrl) {
        this.shifts = shifts;
        this.assignments = assignments;
        this.employees = employees;
        this.leaves = leaves;
        this.users = users;
        this.ai = RestClient.builder().baseUrl(aiUrl).build();
    }

    public record GenerateForm(LocalDate startDate, LocalDate endDate, Integer maxShiftsPerWeek) {}

    public record RequiredForm(Integer required) {}

    public record RosterEntry(Long id, LocalDate date, Long shiftId, String shiftName,
                              Long employeeId, String employeeName) {}

    private ResponseEntity<?> error(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("error", text));
    }

    private RosterEntry toEntry(ShiftAssignment a) {
        Employee e = a.getEmployee();
        return new RosterEntry(a.getId(), a.getWorkDate(), a.getShift().getId(), a.getShift().getName(),
                e.getId(), e.getFirstName() + " " + e.getLastName());
    }

    private List<RosterEntry> sortedEntries(List<ShiftAssignment> list) {
        return list.stream()
                .sorted(Comparator.comparing(ShiftAssignment::getWorkDate)
                        .thenComparing(a -> a.getShift().getStartTime())
                        .thenComparing(a -> a.getEmployee().getFirstName()))
                .map(this::toEntry)
                .toList();
    }

    @GetMapping
    public List<Shift> list() {
        return shifts.findAllByOrderByStartTimeAsc();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRequired(@PathVariable Long id, @RequestBody RequiredForm form) {
        Shift s = shifts.findById(id).orElse(null);
        if (s == null) {
            return ResponseEntity.notFound().build();
        }
        if (form.required() == null || form.required() < 1 || form.required() > 20) {
            return error(400, "Required staff must be between 1 and 20");
        }
        s.setRequiredStaff(form.required());
        return ResponseEntity.ok(shifts.save(s));
    }

    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<?> generate(@RequestBody GenerateForm form) {
        LocalDate start = form.startDate();
        LocalDate end = form.endDate();
        if (start == null || end == null) {
            return error(400, "Start date and end date are required");
        }
        if (end.isBefore(start)) {
            return error(400, "End date cannot be before start date");
        }
        if (ChronoUnit.DAYS.between(start, end) >= 31) {
            return error(400, "Pick a range of 31 days or less");
        }
        int maxPerWeek = form.maxShiftsPerWeek() == null ? 5 : form.maxShiftsPerWeek();
        if (maxPerWeek < 1 || maxPerWeek > 7) {
            return error(400, "Max shifts per week must be between 1 and 7");
        }

        List<Shift> shiftList = shifts.findAllByOrderByStartTimeAsc();
        if (shiftList.isEmpty()) {
            return error(400, "No shifts are defined");
        }
        List<Employee> staff = employees.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .toList();
        if (staff.isEmpty()) {
            return error(400, "There are no active employees to schedule");
        }

        List<String> dates = start.datesUntil(end.plusDays(1)).map(LocalDate::toString).toList();

        List<Map<String, Object>> shiftPayload = new ArrayList<>();
        Map<Long, Shift> shiftById = new HashMap<>();
        for (Shift s : shiftList) {
            shiftById.put(s.getId(), s);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("start", s.getStartTime().format(HHMM));
            m.put("end", s.getEndTime().format(HHMM));
            m.put("required", s.getRequiredStaff());
            shiftPayload.add(m);
        }

        List<Map<String, Object>> employeePayload = new ArrayList<>();
        Map<Long, Employee> employeeById = new HashMap<>();
        for (Employee e : staff) {
            employeeById.put(e.getId(), e);
            // approved leave inside the range becomes "unavailable" for the solver
            List<String> off = new ArrayList<>();
            for (LeaveRequest r : leaves.findByEmployeeIdAndStatus(e.getId(), "APPROVED")) {
                LocalDate d = r.getStartDate().isAfter(start) ? r.getStartDate() : start;
                LocalDate last = r.getEndDate().isBefore(end) ? r.getEndDate() : end;
                while (!d.isAfter(last)) {
                    off.add(d.toString());
                    d = d.plusDays(1);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("name", e.getFirstName() + " " + e.getLastName());
            m.put("unavailable", off);
            employeePayload.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dates", dates);
        body.put("shifts", shiftPayload);
        body.put("employees", employeePayload);
        body.put("max_shifts_per_week", maxPerWeek);

        Map<String, Object> result;
        try {
            result = ai.post()
                    .uri("/schedule/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException ex) {
            return error(502, "The AI service rejected the request: " + ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            return error(503, "The AI service is not running on port 8000. Start it with: uvicorn main:app --port 8000");
        }
        if (result == null) {
            return error(502, "The AI service returned an empty answer");
        }

        String status = String.valueOf(result.get("status"));
        if (!status.equals("OPTIMAL") && !status.equals("FEASIBLE")) {
            return error(422, String.valueOf(result.getOrDefault("message", "No roster could be created")));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("assignments");

        List<ShiftAssignment> toSave = new ArrayList<>();
        for (Map<String, Object> a : list) {
            Employee emp = employeeById.get(((Number) a.get("employee_id")).longValue());
            Shift shift = shiftById.get(((Number) a.get("shift_id")).longValue());
            if (emp == null || shift == null) {
                continue;
            }
            ShiftAssignment sa = new ShiftAssignment();
            sa.setEmployee(emp);
            sa.setShift(shift);
            sa.setWorkDate(LocalDate.parse((String) a.get("date")));
            toSave.add(sa);
        }

        // replace any older roster in this date range
        assignments.deleteInRange(start, end);
        assignments.saveAll(toSave);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("saved", toSave.size());
        out.put("shiftsPerEmployee", result.get("shifts_per_employee"));
        out.put("solveTimeSeconds", result.get("solve_time_seconds"));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/roster")
    public List<RosterEntry> roster(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sortedEntries(assignments.findByWorkDateBetween(from, to));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        Employee emp = users.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        return ResponseEntity.ok(sortedEntries(
                assignments.findByEmployeeIdAndWorkDateBetween(emp.getId(), from, to)));
    }
}
