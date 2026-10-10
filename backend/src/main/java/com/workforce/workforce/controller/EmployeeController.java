package com.workforce.workforce.controller;

import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.repository.EmployeeRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository repo;

    public EmployeeController(EmployeeRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Employee> getAll() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getOne(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Employee create(@Valid @RequestBody Employee e) {
        e.setId(null);
        return repo.save(e);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> update(@PathVariable Long id, @Valid @RequestBody Employee e) {
        return repo.findById(id).map(existing -> {
            existing.setEmployeeCode(e.getEmployeeCode());
            existing.setFirstName(e.getFirstName());
            existing.setLastName(e.getLastName());
            existing.setEmail(e.getEmail());
            existing.setPhone(e.getPhone());
            existing.setJobTitle(e.getJobTitle());
            existing.setDepartment(e.getDepartment());
            existing.setBaseSalary(e.getBaseSalary());
            existing.setHireDate(e.getHireDate());
            existing.setStatus(e.getStatus());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
