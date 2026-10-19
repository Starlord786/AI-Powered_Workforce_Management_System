package com.workforce.workforce.controller;

import com.workforce.workforce.entity.AttritionPrediction;
import com.workforce.workforce.entity.Employee;
import com.workforce.workforce.entity.EmployeeMetrics;
import com.workforce.workforce.repository.AttritionPredictionRepository;
import com.workforce.workforce.repository.EmployeeMetricsRepository;
import com.workforce.workforce.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI feature 3: attrition (resignation) risk. ADMIN / HR only, see SecurityConfig. */
@RestController
@RequestMapping("/api/ai/attrition")
public class AttritionController {

    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]*)\"");
    private static final double DEFAULT_INCOME = 4900;   // used when an employee has no salary

    private final EmployeeRepository employees;
    private final EmployeeMetricsRepository metricsRepo;
    private final AttritionPredictionRepository predictions;
    private final RestClient ai;
    private final double incomeDivisor;

    public AttritionController(EmployeeRepository employees,
                               EmployeeMetricsRepository metricsRepo,
                               AttritionPredictionRepository predictions,
                               @Value("${ai.service.url:http://localhost:8000}") String aiUrl,
                               @Value("${attrition.income-divisor:1}") double incomeDivisor) {
        this.employees = employees;
        this.metricsRepo = metricsRepo;
        this.predictions = predictions;
        this.ai = RestClient.builder().baseUrl(aiUrl).build();
        this.incomeDivisor = incomeDivisor <= 0 ? 1 : incomeDivisor;
    }

    public record Metrics(Integer age, Integer distanceFromHome, Integer jobLevel,
                          Integer jobSatisfaction, Integer workLifeBalance,
                          Integer environmentSatisfaction, Boolean overtime,
                          Integer yearsAtCompany, Integer yearsSinceLastPromotion,
                          Integer totalWorkingYears, Integer numCompaniesWorked,
                          Boolean demoData) {}

    public record ResultRow(Long employeeId, String employeeName, String jobTitle, String department,
                            double riskScore, String riskLevel, List<String> factors,
                            String action, LocalDateTime predictedAt) {}

    private ResponseEntity<?> error(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("error", text));
    }

    private ResponseEntity<?> aiError(RestClientException ex) {
        if (ex instanceof RestClientResponseException r) {
            Matcher m = DETAIL.matcher(r.getResponseBodyAsString());
            return error(502, m.find() ? m.group(1) : "The AI service returned an error");
        }
        return error(503, "The AI service is not running on port 8000");
    }

    // ---------- demo data ----------

    private int between(Random r, int lo, int hi) {
        return lo + r.nextInt(hi - lo + 1);
    }

    /** Realistic made-up values, always the same for the same employee. Every 4th employee is "at risk". */
    private EmployeeMetrics demoMetrics(Employee e) {
        boolean atRisk = e.getId() % 4 == 0;
        Random r = new Random(e.getId() * 7919L);

        int age = atRisk ? between(r, 21, 32) : between(r, 24, 56);
        int total = between(r, 0, age - 18);
        int years = atRisk ? between(r, 0, Math.min(total, 4)) : between(r, 0, Math.min(total, 12));
        int promo = atRisk ? between(r, Math.min(2, years), years) : between(r, 0, Math.min(years, 4));

        EmployeeMetrics m = new EmployeeMetrics();
        m.setEmployee(e);
        m.setAge(age);
        m.setDistanceFromHome(between(r, 1, 29));
        m.setJobLevel(atRisk ? 1 : between(r, 1, 4));
        m.setJobSatisfaction(atRisk ? between(r, 1, 2) : between(r, 2, 4));
        m.setWorkLifeBalance(atRisk ? between(r, 1, 2) : between(r, 2, 4));
        m.setEnvironmentSatisfaction(atRisk ? between(r, 1, 2) : between(r, 2, 4));
        m.setOvertime(atRisk || r.nextInt(4) == 0);
        m.setYearsAtCompany(years);
        m.setYearsSinceLastPromotion(promo);
        m.setTotalWorkingYears(total);
        m.setNumCompaniesWorked(between(r, 0, 6));
        m.setDemoData(true);
        return m;
    }

    private EmployeeMetrics ensureMetrics(Employee e) {
        return metricsRepo.findByEmployeeId(e.getId()).orElseGet(() -> metricsRepo.save(demoMetrics(e)));
    }

    private Metrics toView(EmployeeMetrics m) {
        return new Metrics(m.getAge(), m.getDistanceFromHome(), m.getJobLevel(), m.getJobSatisfaction(),
                m.getWorkLifeBalance(), m.getEnvironmentSatisfaction(), m.getOvertime(),
                m.getYearsAtCompany(), m.getYearsSinceLastPromotion(), m.getTotalWorkingYears(),
                m.getNumCompaniesWorked(), m.getDemoData());
    }

    private String check(String name, Integer v, int lo, int hi) {
        if (v == null || v < lo || v > hi) return name + " must be between " + lo + " and " + hi;
        return null;
    }

    private String validate(Metrics m) {
        if (m.overtime() == null) return "Overtime is required";
        String[] problems = {
                check("Age", m.age(), 16, 80),
                check("Distance from home", m.distanceFromHome(), 0, 200),
                check("Job level", m.jobLevel(), 1, 5),
                check("Job satisfaction", m.jobSatisfaction(), 1, 4),
                check("Work-life balance", m.workLifeBalance(), 1, 4),
                check("Environment satisfaction", m.environmentSatisfaction(), 1, 4),
                check("Years at company", m.yearsAtCompany(), 0, 60),
                check("Years since promotion", m.yearsSinceLastPromotion(), 0, 60),
                check("Total working years", m.totalWorkingYears(), 0, 60),
                check("Companies worked for", m.numCompaniesWorked(), 0, 30)
        };
        for (String p : problems) if (p != null) return p;
        return null;
    }

    // ---------- endpoints ----------

    @GetMapping("/metrics/{employeeId}")
    @Transactional
    public ResponseEntity<?> getMetrics(@PathVariable Long employeeId) {
        Employee e = employees.findById(employeeId).orElse(null);
        if (e == null) return error(404, "Employee not found");
        return ResponseEntity.ok(toView(ensureMetrics(e)));
    }

    @PutMapping("/metrics/{employeeId}")
    @Transactional
    public ResponseEntity<?> saveMetrics(@PathVariable Long employeeId, @RequestBody Metrics form) {
        Employee e = employees.findById(employeeId).orElse(null);
        if (e == null) return error(404, "Employee not found");
        String problem = validate(form);
        if (problem != null) return error(400, problem);

        EmployeeMetrics m = metricsRepo.findByEmployeeId(employeeId).orElseGet(() -> {
            EmployeeMetrics created = new EmployeeMetrics();
            created.setEmployee(e);
            return created;
        });
        m.setAge(form.age());
        m.setDistanceFromHome(form.distanceFromHome());
        m.setJobLevel(form.jobLevel());
        m.setJobSatisfaction(form.jobSatisfaction());
        m.setWorkLifeBalance(form.workLifeBalance());
        m.setEnvironmentSatisfaction(form.environmentSatisfaction());
        m.setOvertime(form.overtime());
        m.setYearsAtCompany(form.yearsAtCompany());
        m.setYearsSinceLastPromotion(form.yearsSinceLastPromotion());
        m.setTotalWorkingYears(form.totalWorkingYears());
        m.setNumCompaniesWorked(form.numCompaniesWorked());
        m.setDemoData(false);
        return ResponseEntity.ok(toView(metricsRepo.save(m)));
    }

    @PostMapping("/run")
    @Transactional
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> run() {
        List<Employee> staff = employees.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .toList();
        if (staff.isEmpty()) return error(400, "There are no active employees");

        Map<Long, Employee> byId = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee e : staff) {
            EmployeeMetrics m = ensureMetrics(e);
            double income = e.getBaseSalary() == null ? DEFAULT_INCOME
                    : e.getBaseSalary().doubleValue() / incomeDivisor;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("age", m.getAge());
            row.put("distance_from_home", m.getDistanceFromHome());
            row.put("job_level", m.getJobLevel());
            row.put("job_satisfaction", m.getJobSatisfaction());
            row.put("work_life_balance", m.getWorkLifeBalance());
            row.put("environment_satisfaction", m.getEnvironmentSatisfaction());
            row.put("overtime", Boolean.TRUE.equals(m.getOvertime()));
            row.put("monthly_income", income);
            row.put("years_at_company", m.getYearsAtCompany());
            row.put("years_since_last_promotion", m.getYearsSinceLastPromotion());
            row.put("total_working_years", m.getTotalWorkingYears());
            row.put("num_companies_worked", m.getNumCompaniesWorked());
            rows.add(row);
            byId.put(e.getId(), e);
        }

        Map<String, Object> response;
        try {
            response = ai.post().uri("/attrition/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("employees", rows))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException ex) {
            return aiError(ex);
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        predictions.deleteAllInBatch();   // keep only the latest run

        LocalDateTime now = LocalDateTime.now();
        int high = 0;
        for (Map<String, Object> r : results) {
            Employee e = byId.get(((Number) r.get("id")).longValue());
            if (e == null) continue;

            List<String> factors = new ArrayList<>();
            for (Map<String, Object> f : (List<Map<String, Object>>) r.get("factors")) {
                factors.add(f.get("label") + " (+" + f.get("impact") + "%)");
            }

            AttritionPrediction p = new AttritionPrediction();
            p.setEmployee(e);
            p.setRiskScore(((Number) r.get("risk_score")).doubleValue());
            p.setRiskLevel((String) r.get("risk_level"));
            p.setTopFactors(String.join(";", factors));
            p.setSuggestedAction((String) r.get("action"));
            p.setPredictedAt(now);
            predictions.save(p);
            if ("HIGH".equals(p.getRiskLevel())) high++;
        }
        return ResponseEntity.ok(Map.of("predicted", results.size(), "highRisk", high));
    }

    @GetMapping("/results")
    @Transactional(readOnly = true)
    public List<ResultRow> results() {
        return predictions.findAll().stream()
                .sorted(Comparator.comparing(AttritionPrediction::getRiskScore).reversed())
                .map(p -> {
                    Employee e = p.getEmployee();
                    List<String> factors = (p.getTopFactors() == null || p.getTopFactors().isBlank())
                            ? List.of()
                            : Arrays.stream(p.getTopFactors().split(";")).map(String::trim).toList();
                    return new ResultRow(e.getId(), e.getFirstName() + " " + e.getLastName(),
                            e.getJobTitle(),
                            e.getDepartment() == null ? null : e.getDepartment().getName(),
                            p.getRiskScore(), p.getRiskLevel(), factors,
                            p.getSuggestedAction(), p.getPredictedAt());
                })
                .toList();
    }

    @GetMapping("/model-info")
    public ResponseEntity<?> modelInfo() {
        try {
            Map<String, Object> info = ai.get().uri("/attrition/model-info")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return ResponseEntity.ok(info);
        } catch (RestClientException ex) {
            return aiError(ex);
        }
    }
}
