package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.Task;
import com.workforce.workforce.repository.EmployeeRepository;
import com.workforce.workforce.repository.TaskRepository;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> APPROVERS = Set.of("ADMIN", "HR", "MANAGER");

    private final TaskRepository tasks;
    private final EmployeeRepository employees;
    private final UserRepository users;

    public TaskController(TaskRepository tasks, EmployeeRepository employees, UserRepository users) {
        this.tasks = tasks;
        this.employees = employees;
        this.users = users;
    }

    public record TaskForm(String title, String description, Long assignedToId,
                           String priority, LocalDate dueDate) {}

    public record StatusForm(String status) {}

    private Employee currentEmployee(Jwt jwt) {
        return users.findByEmail(jwt.getSubject()).map(AppUser::getEmployee).orElse(null);
    }

    private ResponseEntity<?> error(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("error", text));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TaskForm form, @AuthenticationPrincipal Jwt jwt) {
        if (form.title() == null || form.title().isBlank()) {
            return error(400, "Title is required");
        }
        if (form.assignedToId() == null) {
            return error(400, "Pick an employee to assign the task to");
        }
        Employee assignee = employees.findById(form.assignedToId()).orElse(null);
        if (assignee == null) {
            return error(400, "Unknown employee");
        }

        Task t = new Task();
        t.setTitle(form.title().trim());
        t.setDescription(form.description());
        t.setAssignedTo(assignee);
        t.setPriority(form.priority() != null && PRIORITIES.contains(form.priority())
                ? form.priority() : "MEDIUM");
        t.setDueDate(form.dueDate());
        t.setCreatedBy(jwt.getSubject());
        t.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(tasks.save(t));
    }

    @GetMapping
    public List<Task> all() {
        return tasks.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@AuthenticationPrincipal Jwt jwt) {
        Employee emp = currentEmployee(jwt);
        if (emp == null) {
            return error(400, "Your account is not linked to an employee record");
        }
        return ResponseEntity.ok(tasks.findByAssignedToIdOrderByDueDateAsc(emp.getId()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusForm form,
                                          @AuthenticationPrincipal Jwt jwt) {
        Task t = tasks.findById(id).orElse(null);
        if (t == null) {
            return ResponseEntity.notFound().build();
        }
        if (form.status() == null || !STATUSES.contains(form.status())) {
            return error(400, "Status must be TODO, IN_PROGRESS or DONE");
        }

        boolean approver = APPROVERS.contains(jwt.getClaimAsString("role"));
        if (!approver) {
            Employee me = currentEmployee(jwt);
            if (me == null || !me.getId().equals(t.getAssignedTo().getId())) {
                return error(403, "You can only update your own tasks");
            }
        }

        t.setStatus(form.status());
        return ResponseEntity.ok(tasks.save(t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!tasks.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tasks.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
