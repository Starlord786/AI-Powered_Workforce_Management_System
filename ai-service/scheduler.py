from datetime import date, datetime, timedelta
from typing import Dict, List, Optional

from fastapi import APIRouter
from ortools.sat.python import cp_model
from pydantic import BaseModel, Field

router = APIRouter()


class ShiftIn(BaseModel):
    id: int
    name: str
    start: str                      # "06:00"
    end: str                        # "14:00" (an end earlier than the start means it ends next day)
    required: int = Field(ge=1)     # how many people each day


class EmployeeIn(BaseModel):
    id: int
    name: str
    unavailable: List[date] = []            # approved leave or days off
    preferred_shift_ids: List[int] = []     # optional soft preference


class ScheduleRequest(BaseModel):
    dates: List[date]
    shifts: List[ShiftIn]
    employees: List[EmployeeIn]
    max_shifts_per_week: int = 5
    min_rest_hours: float = 11
    time_limit_seconds: int = 10


def _minutes(hhmm: str) -> int:
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def _bounds(shift: ShiftIn):
    """Start and end of a shift in minutes from midnight of its own day."""
    start = _minutes(shift.start)
    end = _minutes(shift.end)
    if end <= start:
        end += 24 * 60
    return start, end


@router.post("/schedule/generate")
def generate(req: ScheduleRequest):
    dates = sorted(set(req.dates))
    D, S, E = len(dates), len(req.shifts), len(req.employees)

    if D == 0 or S == 0 or E == 0:
        return {"status": "INVALID", "message": "Dates, shifts and employees are all required."}

    # quick sanity check before running the solver
    daily_need = sum(s.required for s in req.shifts)
    if daily_need > E:
        return {
            "status": "INFEASIBLE",
            "message": f"Each day needs {daily_need} people but only {E} employees exist "
                       f"(one person can work only one shift per day).",
        }

    model = cp_model.CpModel()
    x = {}
    for e in range(E):
        for d in range(D):
            for s in range(S):
                x[e, d, s] = model.NewBoolVar(f"x_{e}_{d}_{s}")

    # 1. every shift gets exactly the required number of people
    for d in range(D):
        for s in range(S):
            model.Add(sum(x[e, d, s] for e in range(E)) == req.shifts[s].required)

    # 2. one shift per day per employee
    for e in range(E):
        for d in range(D):
            model.Add(sum(x[e, d, s] for s in range(S)) <= 1)

    # 3. no shifts on days the employee is unavailable (leave)
    for e, emp in enumerate(req.employees):
        off = set(emp.unavailable)
        for d, day in enumerate(dates):
            if day in off:
                for s in range(S):
                    model.Add(x[e, d, s] == 0)

    # 4. weekly cap, counted in blocks of 7 days from the first date
    for e in range(E):
        for start in range(0, D, 7):
            block = range(start, min(start + 7, D))
            model.Add(sum(x[e, d, s] for d in block for s in range(S)) <= req.max_shifts_per_week)

    # 5. minimum rest between a shift and the next day's shift
    bounds = [_bounds(sh) for sh in req.shifts]
    min_rest = int(req.min_rest_hours * 60)
    for d in range(D - 1):
        if (dates[d + 1] - dates[d]) != timedelta(days=1):
            continue  # not consecutive days, nothing to check
        for s1 in range(S):
            for s2 in range(S):
                rest = (24 * 60 + bounds[s2][0]) - bounds[s1][1]
                if rest < min_rest:
                    for e in range(E):
                        model.Add(x[e, d, s1] + x[e, d + 1, s2] <= 1)

    # fairness: minimise the gap between the busiest and the least busy employee
    totals = []
    for e in range(E):
        t = model.NewIntVar(0, D, f"total_{e}")
        model.Add(t == sum(x[e, d, s] for d in range(D) for s in range(S)))
        totals.append(t)
    most = model.NewIntVar(0, D, "most")
    least = model.NewIntVar(0, D, "least")
    model.AddMaxEquality(most, totals)
    model.AddMinEquality(least, totals)

    # soft preference: a small bonus when someone gets a shift they asked for
    shift_index = {sh.id: i for i, sh in enumerate(req.shifts)}
    preferred_hits = []
    for e, emp in enumerate(req.employees):
        for sid in emp.preferred_shift_ids:
            if sid in shift_index:
                for d in range(D):
                    preferred_hits.append(x[e, d, shift_index[sid]])

    model.Minimize(100 * (most - least) - sum(preferred_hits))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = req.time_limit_seconds
    solver.parameters.num_workers = 4
    status = solver.Solve(model)

    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return {
            "status": "INFEASIBLE",
            "message": "No roster satisfies all the rules. Try fewer required staff, "
                       "a higher weekly limit, a lower rest requirement, or add employees.",
        }

    assignments = []
    roster = []
    counts = {emp.id: 0 for emp in req.employees}
    for d, day in enumerate(dates):
        row: Dict[str, List[str]] = {}
        for s, sh in enumerate(req.shifts):
            names = []
            for e, emp in enumerate(req.employees):
                if solver.Value(x[e, d, s]):
                    assignments.append({"date": day.isoformat(), "shift_id": sh.id, "employee_id": emp.id})
                    names.append(emp.name)
                    counts[emp.id] += 1
            row[sh.name] = names
        roster.append({"date": day.isoformat(), "weekday": day.strftime("%a"), "shifts": row})

    return {
        "status": "OPTIMAL" if status == cp_model.OPTIMAL else "FEASIBLE",
        "assignments": assignments,
        "roster": roster,
        "shifts_per_employee": {emp.name: counts[emp.id] for emp in req.employees},
        "solve_time_seconds": round(solver.WallTime(), 3),
    }
