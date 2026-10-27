# AI Powered Workforce Management System

Spring Boot backend + React/Electron frontend + Python AI service (face recognition attendance, OR-Tools shift scheduling, attrition prediction, HR chatbot via Ollama).

## Setup

**Requirements:** Java 21, PostgreSQL, Node.js, Python 3.12, (optional) Ollama for the chatbot.

### 1. Database
Create a PostgreSQL database named `workforce_db`.

### 2. Backend config (secrets stay out of git)
```bash
cd backend
cp .env.example .env      # then edit .env
```
Set at least:
- `DB_PASSWORD` - your PostgreSQL password
- `JWT_SECRET` - random string, 32+ chars (`openssl rand -base64 48`)

For the **first run** on an empty database also set `SEED_DEMO_USERS=true`. It creates
`admin@`, `hr@`, `manager@`, `employee@workforce.com`. Passwords come from `SEED_*_PASSWORD`
in `.env`; any left blank are generated randomly and printed once in the console.

```bash
./mvnw spring-boot:run
```

### 3. AI service
```bash
cd ai-service
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
# put the IBM HR Attrition CSV in ai-service/data/, then:
python train_attrition.py
uvicorn main:app --port 8000
```

### 4. Frontend
```bash
cd frontend
npm install
npm run dev
```

## Security notes
- Never commit `.env`. It is git-ignored; only `.env.example` (placeholders) is tracked.
- Change/remove demo users before any real deployment.
