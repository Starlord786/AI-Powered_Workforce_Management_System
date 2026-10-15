package com.workforce.workforce.config;

import com.workforce.workforce.entity.Shift;
import com.workforce.workforce.repository.ShiftRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

// Creates the default shifts the first time the app starts.
@Component
public class ShiftSeeder implements CommandLineRunner {

    private final ShiftRepository repo;

    public ShiftSeeder(ShiftRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        add("Morning", 6, 14, 2);
        add("Evening", 14, 22, 2);
        add("Night", 22, 6, 1);
    }

    private void add(String name, int startHour, int endHour, int required) {
        Shift s = new Shift();
        s.setName(name);
        s.setStartTime(LocalTime.of(startHour, 0));
        s.setEndTime(LocalTime.of(endHour, 0));
        s.setRequiredStaff(required);
        repo.save(s);
    }
}
