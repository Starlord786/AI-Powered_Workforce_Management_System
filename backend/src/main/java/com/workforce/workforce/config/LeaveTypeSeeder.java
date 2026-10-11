package com.workforce.workforce.config;

import com.workforce.workforce.entity.LeaveType;
import com.workforce.workforce.repository.LeaveTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Creates the default leave types the first time the app starts.
@Component
public class LeaveTypeSeeder implements CommandLineRunner {

    private final LeaveTypeRepository repo;

    public LeaveTypeSeeder(LeaveTypeRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        add("Annual", 14);
        add("Sick", 10);
        add("Casual", 7);
    }

    private void add(String name, int days) {
        LeaveType t = new LeaveType();
        t.setName(name);
        t.setDaysPerYear(days);
        repo.save(t);
    }
}
