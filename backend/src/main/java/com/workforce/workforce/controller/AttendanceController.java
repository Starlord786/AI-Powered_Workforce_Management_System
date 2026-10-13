package com.workforce.workforce.controller;

import com.workforce.workforce.ai.FaceClient;
import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Attendance;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.FaceData;
import com.workforce.workforce.repository.AttendanceRepository;
import com.workforce.workforce.repository.EmployeeRepository;
import com.workforce.workforce.repository.FaceDataRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final EmployeeRepository employeeRepo;
    private final FaceDataRepository faceRepo;
    private final FaceClient faceClient;

    public AttendanceController(AttendanceRepository attendanceRepo, UserRepository userRepo,
                                EmployeeRepository employeeRepo, FaceDataRepository faceRepo,
                                FaceClient faceClient) {
        this.attendanceRepo = attendanceRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.faceRepo = faceRepo;
        this.faceClient = faceClient;
    }

    private Employee currentEmployee(Jwt jwt) {
        return userRepo.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
    }

    private ResponseEntity<?> notLinked() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Your account is not linked to an employee record"));
    }

    // ---------- shared logic (manual + face) ----------

    private ResponseEntity<?> doCheckIn(Employee emp, String method, Double confidence) {
        LocalDate today = LocalDate.now();
        if (attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), today).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "You already checked in today"));
        }

        LocalDateTime now = LocalDateTime.now();
        Attendance a = new Attendance();
        a.setEmployee(emp);
        a.setWorkDate(today);
        a.setCheckIn(now);
        a.setMethod(method);
        a.setConfidence(confidence);
        a.setStatus(now.toLocalTime().isAfter(LATE_AFTER) ? "LATE" : "PRESENT");
        return ResponseEntity.ok(attendanceRepo.save(a));
    }

    private ResponseEntity<?> doCheckOut(Employee emp) {
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

    // ---------- manual ----------

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) return notLinked();
        return doCheckIn(emp, "MANUAL", null);
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) return notLinked();
        return doCheckOut(emp);
    }

    // ---------- face ----------
    // The face decides WHO is checked in (kiosk style), not the logged-in account.

    @PostMapping("/face-check-in")
    public ResponseEntity<?> faceCheckIn(@RequestParam("file") MultipartFile file) throws IOException {
        return faceAction(file, true);
    }

    @PostMapping("/face-check-out")
    public ResponseEntity<?> faceCheckOut(@RequestParam("file") MultipartFile file) throws IOException {
        return faceAction(file, false);
    }

    private ResponseEntity<?> faceAction(MultipartFile file, boolean checkIn) throws IOException {
        List<FaceData> faces = faceRepo.findAll();
        if (faces.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No faces registered yet"));
        }

        FaceClient.Match m;
        try {
            m = faceClient.match(file.getBytes(), faces);
        } catch (FaceClient.FaceException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
        if (!m.matched()) {
            return ResponseEntity.status(404).body(Map.of("error", m.reason()));
        }

        Employee emp = employeeRepo.findById(m.employeeId()).orElse(null);
        if (emp == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Matched employee no longer exists"));
        }
        if (!"ACTIVE".equals(emp.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "This employee is not active"));
        }

        ResponseEntity<?> result = checkIn ? doCheckIn(emp, "FACE", m.confidence()) : doCheckOut(emp);
        if (!result.getStatusCode().is2xxSuccessful()) return result;

        return ResponseEntity.ok(Map.of(
                "employee", emp.getFirstName() + " " + emp.getLastName(),
                "confidence", m.confidence(),
                "attendance", result.getBody()));
    }

    // ---------- reads ----------

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
