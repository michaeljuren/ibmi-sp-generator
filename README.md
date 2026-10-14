# IBMi Stored Procedure/User Defined Function Generator

Paste a query and get either `CREATE OR REPLACE PROCEDURE` source (SQL PL, `DYNAMIC RESULT SETS 1`,
`WITH RETURN TO CLIENT` cursor) or a `CREATE OR REPLACE FUNCTION` table function (User Defined Function).
The query is **never executed**: Db2 only *prepares* it, and the result-column and parameter types are read
from the prepared statement's metadata.

- Any single `SELECT`, `WITH` (CTE) or `VALUES` statement is accepted: joins, `UNION`, subselects, expressions, etc.
- Result columns (used for the function's `RETURNS TABLE`) come from Db2; unaliased expressions are named `EXPR_n`. Procedures don't need them.
- Host variables (`:name`) and `?` markers become `IN` parameters (`?` is rewritten to `:P1`, `:P2`, ...), typed by Db2. A repeated `:name` takes the type of its first use.
- Types Db2 can't determine (e.g. `SELECT ?`, `? IS NULL`) are flagged; enter the type in the UI.
- Only one statement is allowed; `DML`/`DDL` and anything after a `;` are rejected. A query Db2 rejects returns its error message.
- Unqualified table names resolve through the connection's library list (`*LIBL`); qualify them as `LIB.FILE` to be explicit. SQL naming (`.`) only.

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
