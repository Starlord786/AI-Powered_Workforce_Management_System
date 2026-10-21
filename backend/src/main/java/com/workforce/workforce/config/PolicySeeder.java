package com.workforce.workforce.config;

import com.workforce.workforce.entity.Policy;
import com.workforce.workforce.repository.PolicyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Sample company policies for the chatbot. Edit the text in the database (table "policies") to match your company.
@Component
public class PolicySeeder implements CommandLineRunner {

    private final PolicyRepository repo;

    public PolicySeeder(PolicyRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        add("Leave policy",
                "leave,vacation,holiday,annual,casual,days off,apply,approve,approval,balance",
                "Each employee gets 14 annual, 10 sick and 7 casual leave days per year. Only weekdays are counted. "
                + "Apply on the Leave page. A manager or HR approves or rejects the request. "
                + "You cannot apply for more days than your remaining balance.");
        add("Sick leave",
                "sick,ill,illness,medical,doctor,certificate,unwell",
                "Sick leave is 10 days per year. Tell your manager on the first day. "
                + "A medical certificate is needed when you are sick for 3 or more days in a row.");
        add("Working hours and lateness",
                "late,lateness,hours,working,time,attendance,punctual,check in,check out,checkin,checkout",
                "Check in when you start work and check out when you finish, every working day. "
                + "Checking in after 9:15 AM is marked LATE. Working hours are calculated from check-in to check-out.");
        add("Shifts and roster",
                "shift,roster,schedule,morning,evening,night,swap,rota",
                "HR generates the shift roster. Employees usually get 4 to 5 shifts per week and days off in between. "
                + "Approved leave days are respected when the roster is made. To swap a shift, ask HR.");
        add("Face check-in",
                "face,camera,register,recognition,photo",
                "You can check in and out with face check-in on the Attendance page. HR registers your face first. "
                + "Only numbers describing your face are stored, not your photos.");
        add("Salary and payroll",
                "salary,pay,payroll,payslip,bonus,deduction,paid,payment",
                "Payroll is calculated monthly by HR from your base salary and your attendance and leave. "
                + "For questions about your payslip, contact HR.");
        add("Contacting HR",
                "hr,contact,help,complain,complaint,email,support,question",
                "For anything not answered here, contact HR at hr@workforce.com.");
    }

    private void add(String title, String keywords, String content) {
        Policy p = new Policy();
        p.setTitle(title);
        p.setKeywords(keywords);
        p.setContent(content);
        repo.save(p);
    }
}
