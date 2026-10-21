package com.workforce.workforce.ai;

import com.workforce.workforce.entity.*;
import com.workforce.workforce.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/**
 * HR chatbot. The facts (leave, attendance, shifts, policies) come from our own database;
 * the local LLM only turns them into a friendly answer.
 * An employee only ever gets their OWN data in the prompt.
 */
@Service
public class ChatService {

    public record Msg(String role, String content) {}
    public record Answer(String answer, double seconds) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String SYSTEM_PROMPT = """
            You are the HR assistant of a company. Answer the employee's question using ONLY the CONTEXT below.
            Rules:
            - Use numbers, dates and times exactly as written in the context.
            - Keep the answer short: 1 to 3 sentences.
            - If the answer is not in the context, say you don't know and suggest contacting HR.
            - Never invent policies or numbers.
            - You only know about the employee you are talking to. Do not give information about other people.
            - Answer ONLY the user's last question. Do not answer or repeat earlier questions.
            - If several items match the question (shifts, leave requests, late days), mention ALL of them.

            CONTEXT:
            """;

    private final OllamaClient ollama;
    private final LeaveRequestRepository leaveRepo;
    private final LeaveTypeRepository leaveTypes;
    private final AttendanceRepository attendanceRepo;
    private final ShiftAssignmentRepository assignmentRepo;
    private final PolicyRepository policyRepo;
    private final EmployeeRepository employeeRepo;

    public ChatService(OllamaClient ollama, LeaveRequestRepository leaveRepo, LeaveTypeRepository leaveTypes,
                       AttendanceRepository attendanceRepo, ShiftAssignmentRepository assignmentRepo,
                       PolicyRepository policyRepo, EmployeeRepository employeeRepo) {
        this.ollama = ollama;
        this.leaveRepo = leaveRepo;
        this.leaveTypes = leaveTypes;
        this.attendanceRepo = attendanceRepo;
        this.assignmentRepo = assignmentRepo;
        this.policyRepo = policyRepo;
        this.employeeRepo = employeeRepo;
    }

    public Answer answer(AppUser user, String question, List<Msg> history) {
        Employee emp = user.getEmployee();

        // Lists must be complete, and a small model drops items, so shift questions are answered
        // straight from the database (instant and always correct).
        String direct = directShiftAnswer(emp, question);
        if (direct != null) {
            return new Answer(direct, 0.0);
        }

        String system = SYSTEM_PROMPT + buildContext(emp, user.getRole(), question);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        if (history != null) {
            List<Msg> recent = history.subList(Math.max(0, history.size() - 2), history.size());
            for (Msg m : recent) {
                if (m == null || m.content() == null || m.content().isBlank()) continue;
                String role = "assistant".equals(m.role()) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", clip(m.content(), 500)));
            }
        }
        messages.add(Map.of("role", "user", "content", clip(question, 500)));

        long start = System.nanoTime();
        String text = ollama.chat(messages);
        double seconds = Math.round((System.nanoTime() - start) / 1e8) / 10.0;
        return new Answer(text, seconds);
    }

    /** Plain "what shift am I on" questions. Returns null when the question is about something else. */
    private String directShiftAnswer(Employee emp, String question) {
        String q = question.toLowerCase(Locale.ROOT);
        boolean asksShift = hasAny(q, "shift", "roster", "rota");
        boolean other = hasAny(q, "policy", "swap", "rule", "change", "request", "how do", "how to",
                "can i", "leave", "late", "salary", "face");
        if (!asksShift || other) return null;

        LocalDate today = LocalDate.now();
        LocalDate from = today;
        LocalDate to = today.plusDays(6);
        String label = "in the next 7 days";
        if (q.contains("tomorrow")) {
            from = today.plusDays(1);
            to = from;
            label = "tomorrow";
        } else if (q.contains("today")) {
            to = today;
            label = "today";
        }

        List<ShiftAssignment> list = assignmentRepo
                .findByEmployeeIdAndWorkDateBetween(emp.getId(), from, to).stream()
                .sorted(Comparator.comparing(ShiftAssignment::getWorkDate)).toList();
        if (list.isEmpty()) {
            return "You have no shift " + label + ". It may be a day off, or the roster is not generated yet.";
        }
        StringBuilder sb = new StringBuilder("You have " + list.size()
                + (list.size() == 1 ? " shift " : " shifts ") + label + ":");
        for (ShiftAssignment a : list) {
            Shift sh = a.getShift();
            sb.append("\n\u2022 ").append(day(a.getWorkDate())).append(": ").append(sh.getName())
                    .append(" shift, ").append(sh.getStartTime().format(TIME))
                    .append(" to ").append(sh.getEndTime().format(TIME));
        }
        return sb.toString();
    }

    private static String clip(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String day(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d;
    }

    private String describeLeave(LeaveRequest r) {
        return r.getLeaveType().getName() + " " + r.getStartDate() + " to " + r.getEndDate()
                + " (" + r.getDays() + " days)";
    }

    // ---------- context ----------

    private String buildContext(Employee emp, Role role, String question) {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();

        sb.append("Today: ").append(day(today)).append("\n");
        sb.append("Employee: ").append(emp.getFirstName()).append(" ").append(emp.getLastName());
        if (emp.getJobTitle() != null) sb.append(", ").append(emp.getJobTitle());
        if (emp.getDepartment() != null) sb.append(", ").append(emp.getDepartment().getName()).append(" department");
        sb.append("\n");

        // Only add the sections the question is about: a shorter prompt means a faster answer.
        String q = question.toLowerCase(Locale.ROOT);
        boolean wantLeave = hasAny(q, "leave", "vacation", "holiday", "annual", "sick", "casual", "day off", "days off", "balance");
        boolean wantAttendance = hasAny(q, "late", "attendance", "check in", "check-in", "checked", "present", "absent", "hours", "punctual", "worked");
        boolean wantShifts = hasAny(q, "shift", "roster", "schedule", "morning", "evening", "night", "rota");
        boolean dataMatched = wantLeave || wantAttendance || wantShifts;
        if (!dataMatched) {
            wantLeave = true;
            wantAttendance = true;
            wantShifts = true;
        }

        // leave
        if (wantLeave) {
            List<LeaveRequest> mine = leaveRepo.findByEmployeeIdOrderByCreatedAtDesc(emp.getId());
            sb.append("\nLEAVE BALANCE (").append(today.getYear()).append("):\n");
            for (LeaveType t : leaveTypes.findAll()) {
                int used = mine.stream()
                        .filter(r -> "APPROVED".equals(r.getStatus()))
                        .filter(r -> r.getLeaveType().getId().equals(t.getId()))
                        .filter(r -> r.getStartDate().getYear() == today.getYear())
                        .mapToInt(LeaveRequest::getDays).sum();
                sb.append("- ").append(t.getName()).append(" leave: ").append(t.getDaysPerYear())
                        .append(" allowed, ").append(used).append(" used, ")
                        .append(t.getDaysPerYear() - used).append(" remaining\n");
            }
            List<String> pending = mine.stream().filter(r -> "PENDING".equals(r.getStatus()))
                    .map(this::describeLeave).toList();
            sb.append("Pending leave requests: ").append(pending.isEmpty() ? "none" : String.join("; ", pending)).append("\n");
            List<String> upcoming = mine.stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()) && !r.getEndDate().isBefore(today))
                    .sorted(Comparator.comparing(LeaveRequest::getStartDate))
                    .limit(3).map(this::describeLeave).toList();
            sb.append("Upcoming approved leave: ").append(upcoming.isEmpty() ? "none" : String.join("; ", upcoming)).append("\n");
        }

        // attendance this month
        if (wantAttendance) {
            LocalDate first = today.withDayOfMonth(1);
            List<Attendance> month = attendanceRepo.findByEmployeeIdAndWorkDateBetween(emp.getId(), first, today);
            List<String> lateDates = month.stream().filter(a -> "LATE".equals(a.getStatus()))
                    .map(a -> a.getWorkDate().toString()).sorted().toList();
            sb.append("\nATTENDANCE THIS MONTH (").append(first.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    .append("): ").append(month.size()).append(" days worked, ").append(lateDates.size()).append(" late");
            if (!lateDates.isEmpty()) sb.append(" (").append(String.join(", ", lateDates)).append(")");
            sb.append("\n");
            Attendance todayRecord = month.stream().filter(a -> today.equals(a.getWorkDate())).findFirst().orElse(null);
            if (todayRecord == null || todayRecord.getCheckIn() == null) {
                sb.append("Today: no check-in yet\n");
            } else {
                sb.append("Today: checked in at ").append(todayRecord.getCheckIn().format(TIME))
                        .append(" (").append(todayRecord.getStatus()).append(")");
                sb.append(todayRecord.getCheckOut() == null ? ", not checked out yet"
                        : ", checked out at " + todayRecord.getCheckOut().format(TIME));
                sb.append("\n");
            }
        }

        // shifts, next 7 days
        if (wantShifts) {
            List<ShiftAssignment> shifts = assignmentRepo
                    .findByEmployeeIdAndWorkDateBetween(emp.getId(), today, today.plusDays(6)).stream()
                    .sorted(Comparator.comparing(ShiftAssignment::getWorkDate)).toList();
            sb.append("\nSHIFTS (next 7 days, ").append(shifts.size()).append(" in total):\n");
            if (shifts.isEmpty()) {
                sb.append("- No shifts assigned (day off, or the roster is not generated yet)\n");
            }
            for (ShiftAssignment a : shifts) {
                Shift s = a.getShift();
                sb.append("- ").append(day(a.getWorkDate())).append(": ").append(s.getName()).append(" shift, ")
                        .append(s.getStartTime().format(TIME)).append(" to ").append(s.getEndTime().format(TIME)).append("\n");
            }
        }

        // company-wide numbers for HR / managers (counts only, no personal data of others)
        if (role != null && !"EMPLOYEE".equals(role.name())) {
            long active = employeeRepo.findAll().stream().filter(e -> "ACTIVE".equals(e.getStatus())).count();
            List<LeaveRequest> all = leaveRepo.findAll();
            long pendingAll = all.stream().filter(r -> "PENDING".equals(r.getStatus())).count();
            long onLeave = all.stream().filter(r -> "APPROVED".equals(r.getStatus())
                    && !r.getStartDate().isAfter(today) && !r.getEndDate().isBefore(today)).count();
            sb.append("\nCOMPANY NUMBERS (you may share counts, never personal details of other people):\n")
                    .append("- Active employees: ").append(active).append("\n")
                    .append("- Pending leave requests: ").append(pendingAll).append("\n")
                    .append("- Employees on approved leave today: ").append(onLeave).append("\n");
        }

        // policies
        List<Policy> policies = pickPolicies(q, dataMatched);
        if (!policies.isEmpty()) {
            sb.append("\nCOMPANY POLICIES:\n");
            for (Policy p : policies) {
                sb.append("- ").append(p.getTitle()).append(": ").append(p.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private static boolean hasAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    /** The 3 policies whose keywords appear most in the question. If none match: none when the question
     *  is about the employee's own data, otherwise all of them. */
    private List<Policy> pickPolicies(String q, boolean dataMatched) {
        List<Policy> all = policyRepo.findAll();
        Map<Policy, Integer> score = new LinkedHashMap<>();
        for (Policy p : all) {
            int s = 0;
            if (p.getKeywords() != null) {
                for (String k : p.getKeywords().split(",")) {
                    String key = k.trim().toLowerCase(Locale.ROOT);
                    if (!key.isEmpty() && q.contains(key)) s++;
                }
            }
            if (s > 0) score.put(p, s);
        }
        if (score.isEmpty()) return dataMatched ? List.of() : all;
        return score.entrySet().stream()
                .sorted(Map.Entry.<Policy, Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();
    }
}