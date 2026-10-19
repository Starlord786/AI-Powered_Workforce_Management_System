import os

import joblib
import numpy as np
import pandas as pd
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

router = APIRouter(prefix="/attrition")

MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "attrition_model.pkl")
_bundle = None


def load_bundle():
    global _bundle
    if _bundle is None:
        if not os.path.exists(MODEL_PATH):
            raise HTTPException(status_code=503,
                                detail="Attrition model is not trained yet. Run: python train_attrition.py")
        _bundle = joblib.load(MODEL_PATH)
    return _bundle


class EmployeeIn(BaseModel):
    id: int
    age: int = Field(ge=16, le=80)
    distance_from_home: int = Field(ge=0, le=200)
    job_level: int = Field(ge=1, le=5)
    job_satisfaction: int = Field(ge=1, le=4)
    work_life_balance: int = Field(ge=1, le=4)
    environment_satisfaction: int = Field(ge=1, le=4)
    overtime: bool
    monthly_income: float = Field(ge=0)
    years_at_company: int = Field(ge=0, le=60)
    years_since_last_promotion: int = Field(ge=0, le=60)
    total_working_years: int = Field(ge=0, le=60)
    num_companies_worked: int = Field(ge=0, le=30)


class PredictIn(BaseModel):
    employees: list[EmployeeIn]


# model column -> field name in EmployeeIn
FIELD = {
    "Age": "age", "DistanceFromHome": "distance_from_home", "JobLevel": "job_level",
    "JobSatisfaction": "job_satisfaction", "WorkLifeBalance": "work_life_balance",
    "EnvironmentSatisfaction": "environment_satisfaction", "OverTime": "overtime",
    "MonthlyIncome": "monthly_income", "YearsAtCompany": "years_at_company",
    "YearsSinceLastPromotion": "years_since_last_promotion",
    "TotalWorkingYears": "total_working_years", "NumCompaniesWorked": "num_companies_worked",
}

NAME = {
    "Age": "Age", "DistanceFromHome": "Distance from home (km)", "JobLevel": "Job level",
    "JobSatisfaction": "Job satisfaction", "WorkLifeBalance": "Work-life balance",
    "EnvironmentSatisfaction": "Work environment satisfaction", "OverTime": "Overtime",
    "MonthlyIncome": "Monthly income", "YearsAtCompany": "Years at company",
    "YearsSinceLastPromotion": "Years since promotion", "TotalWorkingYears": "Total working years",
    "NumCompaniesWorked": "Companies worked for",
}

ACTION = {
    "OverTime": "Review workload and overtime hours",
    "MonthlyIncome": "Consider a salary review",
    "JobSatisfaction": "Hold a one-to-one about job satisfaction",
    "WorkLifeBalance": "Offer flexible hours or remote days",
    "EnvironmentSatisfaction": "Check team and workplace issues",
    "YearsSinceLastPromotion": "Discuss career path and promotion",
    "YearsAtCompany": "Check onboarding and early-career support",
    "DistanceFromHome": "Consider hybrid or remote days",
    "Age": "Offer mentoring and a growth plan",
    "JobLevel": "Discuss growth and responsibilities",
    "TotalWorkingYears": "Offer mentoring and a growth plan",
    "NumCompaniesWorked": "Discuss long-term career goals",
}


def level(score: float) -> str:
    if score < 0.30:
        return "LOW"
    if score < 0.60:
        return "MEDIUM"
    return "HIGH"


def describe(feature: str, value: float) -> str:
    if feature == "OverTime":
        shown = "Yes" if value >= 0.5 else "No"
    elif feature in ("JobSatisfaction", "WorkLifeBalance", "EnvironmentSatisfaction"):
        shown = f"{int(value)}/4"
    elif feature == "MonthlyIncome":
        shown = f"{int(round(value))}"
    else:
        shown = f"{int(value)}"
    return f"{NAME[feature]}: {shown}"


def score(rows: list[dict]) -> list[dict]:
    """rows: dicts keyed by EmployeeIn field names (+ id). Returns risk + explanation per row."""
    bundle = load_bundle()
    model, features, baseline = bundle["model"], bundle["features"], bundle["baseline"]
    n, f = len(rows), len(features)

    X = pd.DataFrame(
        [[float(r[FIELD[c]]) for c in features] for r in rows], columns=features)
    risk = model.predict_proba(X)[:, 1]

    # Explanation: for each feature, swap in the typical value of employees who stayed
    # and see how much the risk drops. Big drop = this feature is pushing the risk up.
    base = np.array([baseline[c] for c in features], dtype=float)
    mod = np.repeat(X.to_numpy(dtype=float), f, axis=0)
    for j in range(f):
        mod[j::f, j] = base[j]
    risk_mod = model.predict_proba(pd.DataFrame(mod, columns=features))[:, 1].reshape(n, f)
    impact = risk[:, None] - risk_mod

    out = []
    for i, r in enumerate(rows):
        order = np.argsort(-impact[i])
        factors = []
        for j in order[:3]:
            if impact[i, j] >= 0.03:
                c = features[j]
                factors.append({
                    "feature": c,
                    "label": describe(c, X.iloc[i, j]),
                    "impact": round(float(impact[i, j]) * 100, 1),
                })
        action = ACTION[factors[0]["feature"]] if factors else "No action needed"
        out.append({
            "id": r["id"],
            "risk_score": round(float(risk[i]), 3),
            "risk_level": level(float(risk[i])),
            "factors": factors,
            "action": action,
        })
    return out


@router.post("/predict")
def predict(body: PredictIn):
    if not body.employees:
        return {"results": []}
    return {"results": score([e.model_dump() for e in body.employees])}


@router.get("/model-info")
def model_info():
    bundle = load_bundle()
    return {**bundle["metrics"], "features": bundle["features"], "importances": bundle["importances"]}
