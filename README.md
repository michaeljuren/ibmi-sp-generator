# IBM i Stored Procedure Generator

Paste a SELECT query, list the libraries/files it uses, and get `CREATE OR REPLACE PROCEDURE` source
(SQL PL, `DYNAMIC RESULT SETS 1`, `WITH RETURN TO CLIENT` cursor). The query is **never executed**:
column types come from `QSYS2.SYSCOLUMNS` only.

- Plain/aliased columns and `*` / `T.*` are resolved from `SYSCOLUMNS`.
- Expressions (e.g. `SUM(x)`) and parameters whose type can't be inferred are flagged; enter the type in the UI.
- Host variables (`:name`) and `?` markers become `IN` parameters (`?` is rewritten to `:P1`, `:P2`, ...), typed from the column they are compared with.
- Limitations: a single plain SELECT only (no UNION/WITH/subselects in FROM); SQL naming (`.`) only.

## Run

Backend (Java 11+, Maven), needs network access to the IBM i:

```
cd backend
$env:IBMI_HOST="myibmi"; $env:IBMI_USER="user"; $env:IBMI_PASSWORD="..."
mvn spring-boot:run
```

Frontend (proxies `/api` to `localhost:8080`):

```
cd frontend
npm install
npx ng serve
```

Open http://localhost:4200. Tests: `mvn test` in `backend`.
