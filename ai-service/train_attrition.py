"""Train the attrition (resignation risk) model.

Run once from the ai-service folder:   python train_attrition.py
Needs:  data/WA_Fn-UseC_-HR-Employee-Attrition.csv   (IBM HR Analytics dataset from Kaggle)
Makes:  models/attrition_model.pkl  + two charts for the report.
"""
import json
import os

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (accuracy_score, classification_report, confusion_matrix,
                             precision_score, recall_score, roc_auc_score)
from sklearn.model_selection import cross_val_score, train_test_split

BASE = os.path.dirname(os.path.abspath(__file__))
CSV = os.path.join(BASE, "data", "WA_Fn-UseC_-HR-Employee-Attrition.csv")
OUT = os.path.join(BASE, "models")

# Only features that our own system can provide for an employee.
FEATURES = [
    "Age", "DistanceFromHome", "JobLevel", "JobSatisfaction", "WorkLifeBalance",
    "EnvironmentSatisfaction", "OverTime", "MonthlyIncome", "YearsAtCompany",
    "YearsSinceLastPromotion", "TotalWorkingYears", "NumCompaniesWorked",
]

if not os.path.exists(CSV):
    raise SystemExit(f"Dataset not found: {CSV}\nDownload WA_Fn-UseC_-HR-Employee-Attrition.csv from Kaggle and put it there.")

os.makedirs(OUT, exist_ok=True)
df = pd.read_csv(CSV)
df["OverTime"] = (df["OverTime"] == "Yes").astype(int)
y = (df["Attrition"] == "Yes").astype(int)
X = df[FEATURES]

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y)

model = RandomForestClassifier(
    n_estimators=300, min_samples_leaf=3, class_weight="balanced",
    random_state=42, n_jobs=-1)
model.fit(X_train, y_train)

pred = model.predict(X_test)
proba = model.predict_proba(X_test)[:, 1]
cm = confusion_matrix(y_test, pred)
auc = roc_auc_score(y_test, proba)
cv_auc = cross_val_score(model, X, y, cv=5, scoring="roc_auc", n_jobs=-1)

print(classification_report(y_test, pred, target_names=["Stayed", "Left"]))
print("Confusion matrix [[TN FP] [FN TP]]:\n", cm)
print(f"AUC: {auc:.3f}   5-fold CV AUC: {cv_auc.mean():.3f} (+/- {cv_auc.std():.3f})")

importances = dict(zip(FEATURES, model.feature_importances_.round(4).tolist()))
print("\nFeature importance:")
for name, value in sorted(importances.items(), key=lambda kv: -kv[1]):
    print(f"  {name:26s} {value:.3f}")

metrics = {
    "accuracy": round(float(accuracy_score(y_test, pred)), 3),
    "precision": round(float(precision_score(y_test, pred, zero_division=0)), 3),
    "recall": round(float(recall_score(y_test, pred, zero_division=0)), 3),
    "auc": round(float(auc), 3),
    "cv_auc": round(float(cv_auc.mean()), 3),
    "confusion_matrix": cm.tolist(),
    "train_rows": int(len(X_train)),
    "test_rows": int(len(X_test)),
    "attrition_rate": round(float(y.mean()), 3),
}

joblib.dump({
    "model": model,
    "features": FEATURES,
    # typical values of employees who stayed: used to explain each prediction
    "baseline": X[y == 0].median().to_dict(),
    "importances": importances,
    "metrics": metrics,
}, os.path.join(OUT, "attrition_model.pkl"))
with open(os.path.join(OUT, "metrics.json"), "w") as f:
    json.dump(metrics, f, indent=2)

# charts for the report
try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    items = sorted(importances.items(), key=lambda kv: kv[1])
    plt.figure(figsize=(7, 5))
    plt.barh([k for k, _ in items], [v for _, v in items], color="#2563eb")
    plt.title("Feature importance")
    plt.tight_layout()
    plt.savefig(os.path.join(OUT, "feature_importance.png"), dpi=150)
    plt.close()

    plt.figure(figsize=(4.5, 4))
    plt.imshow(cm, cmap="Blues")
    for i in range(2):
        for j in range(2):
            plt.text(j, i, cm[i, j], ha="center", va="center", fontsize=14)
    plt.xticks([0, 1], ["Stayed", "Left"])
    plt.yticks([0, 1], ["Stayed", "Left"])
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.title("Confusion matrix")
    plt.tight_layout()
    plt.savefig(os.path.join(OUT, "confusion_matrix.png"), dpi=150)
    plt.close()
    print("\nCharts saved in models/")
except ImportError:
    print("\n(matplotlib not installed, skipped charts: pip install matplotlib)")

print("Model saved to models/attrition_model.pkl")
