package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Attendance;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.repository.AttendanceRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final LocalTime LATE_AFTER = LocalTime.of(9, 15);

    private final AttendanceRepository attendanceRepo;
    private final UserRepository userRepo;

    public AttendanceController(AttendanceRepository attendanceRepo, UserRepository userRepo) {
        this.attendanceRepo = attendanceRepo;
        this.userRepo = userRepo;
    }

    private Employee currentEmployee(Jwt jwt) {
        return userRepo.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
    }

    private ResponseEntity<?> notLinked() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Your account is not linked to an employee record"));
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) return notLinked();

        LocalDate today = LocalDate.now();
        if (attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), today).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "You already checked in today"));
        }

        LocalDateTime now = LocalDateTime.now();
        Attendance a = new Attendance();
        a.setEmployee(emp);
        a.setWorkDate(today);
        a.setCheckIn(now);
        a.setStatus(now.toLocalTime().isAfter(LATE_AFTER) ? "LATE" : "PRESENT");
        return ResponseEntity.ok(attendanceRepo.save(a));
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) return notLinked();

        Attendance a = attendanceRepo
                .findByEmployeeIdAndWorkDate(emp.getId(), LocalDate.now()).orElse(null);
        if (a == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have not checked in today"));
        }
        if (a.getCheckOut() != null) {
            return ResponseEntity.status(409).body(Map.of("error", "You already checked out today"));
        }

        LocalDateTime now = LocalDateTime.now();
        a.setCheckOut(now);
        double hours = Duration.between(a.getCheckIn(), now).toMinutes() / 60.0;
        a.setWorkHours(Math.round(hours * 100.0) / 100.0);
        return ResponseEntity.ok(attendanceRepo.save(a));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) return notLinked();
        return ResponseEntity.ok(attendanceRepo.findByEmployeeIdOrderByWorkDateDesc(emp.getId()));
    }

    @GetMapping
    public List<Attendance> all() {
        return attendanceRepo.findAllByOrderByWorkDateDescCheckInDesc();
    }
}
