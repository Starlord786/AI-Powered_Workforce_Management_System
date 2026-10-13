package com.workforce.workforce.controller;

import com.workforce.workforce.ai.FaceClient;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.FaceData;
import com.workforce.workforce.repository.EmployeeRepository;
import com.workforce.workforce.repository.FaceDataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Face registration. Restricted to ADMIN / HR in SecurityConfig. */
@RestController
@RequestMapping("/api/faces")
public class FaceController {

    private final FaceClient faceClient;
    private final FaceDataRepository faceRepo;
    private final EmployeeRepository employeeRepo;

    public FaceController(FaceClient faceClient, FaceDataRepository faceRepo, EmployeeRepository employeeRepo) {
        this.faceClient = faceClient;
        this.faceRepo = faceRepo;
        this.employeeRepo = employeeRepo;
    }

    @PostMapping("/register/{employeeId}")
    public ResponseEntity<?> register(@PathVariable Long employeeId,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        Employee emp = employeeRepo.findById(employeeId).orElse(null);
        if (emp == null) return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));

        try {
            byte[] image = file.getBytes();

            // refuse a face that already belongs to somebody else
            List<FaceData> others = faceRepo.findAll().stream()
                    .filter(f -> !f.getEmployee().getId().equals(employeeId)).toList();
            if (!others.isEmpty()) {
                FaceClient.Match m = faceClient.match(image, others);
                if (m.matched()) {
                    return ResponseEntity.status(409)
                            .body(Map.of("error", "This face is already registered to another employee"));
                }
            }

            FaceData fd = new FaceData();
            fd.setEmployee(emp);
            fd.setEncoding(faceClient.encode(image));
            faceRepo.save(fd);
            return ResponseEntity.ok(Map.of("employeeId", employeeId,
                    "photos", faceRepo.countByEmployeeId(employeeId)));
        } catch (FaceClient.FaceException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }

    /** employeeId -> number of registered photos */
    @GetMapping("/status")
    public Map<Long, Long> status() {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : faceRepo.countPerEmployee()) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return out;
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<?> clear(@PathVariable Long employeeId) {
        faceRepo.deleteAll(faceRepo.findByEmployeeId(employeeId));
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
