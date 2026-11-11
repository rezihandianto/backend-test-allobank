# Split Bill API

Spring Boot REST API for managing shared expenses in a group and working out who owes whom.
Built for the Allo Bank Backend Developer take-home test.

- **GitHub username:** `rezihandianto`
- **Service charge:** `service_charge_pct = 2` (unicode sum of `rezihandianto` = 1392, `1392 % 10 = 2`)

The percentage is computed in code at startup from the `splitbill.github-username` property
(`SPLITBILL_GITHUB_USERNAME` env var), never hardcoded. `service_charge_amount` is that percentage
applied to the group's total expenses, rounded half-up to 2 decimals. It is reported alongside the
settlement and is **not** added to the transfers.

## Stack

Java 21, Spring Boot 3.5, Maven, `BigDecimal` for every monetary value (scale 2), JUnit 5 + AssertJ + MockMvc.
State is held in memory behind a `GroupRepository` interface, so data is lost on restart.

## Build and run

```bash
# Run tests
mvn test

# Run locally (port 4110)
mvn spring-boot:run

# Or with Docker (multi-stage build)
docker build -t split-bill-api .
docker run --rm -p 4110:4110 split-bill-api

# Override the GitHub username used for the service charge
docker run --rm -p 4110:4110 -e SPLITBILL_GITHUB_USERNAME=someone split-bill-api
```

Health check: `GET http://localhost:4110/actuator/health`

## API

Base path `/api/v1`. JSON is `snake_case`. Errors follow RFC 9457 (`application/problem+json`):
400 invalid input, 404 unknown group, 422 business rule violated (e.g. overpaying a debt).

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/groups` | Create a group with participants |
| GET | `/groups/{id}` | Get a group |
| POST | `/groups/{id}/expenses` | Add an expense (EQUAL / PERCENTAGE / EXACT split) |
| GET | `/groups/{id}/expenses` | List expenses |
| POST | `/groups/{id}/payments` | Record a payment that settles (part of) a debt |
| GET | `/groups/{id}/payments` | List payments |
| GET | `/groups/{id}/settlement` | Balances, minimised transfers, service charge, per-category totals |

### Example requests

```bash
BASE=http://localhost:4110/api/v1

# 1. Create a group
curl -s -X POST $BASE/groups -H 'Content-Type: application/json' \
  -d '{"name":"Bali Trip","participants":["Alice","Bob","Carol"]}'
# -> {"id":"<GROUP_ID>", ...}
GROUP=<GROUP_ID>        # or: GROUP=$(curl -s ... | jq -r .id)

# 2. Get the group
curl -s $BASE/groups/$GROUP

# 3a. Equal split among everyone (splits omitted)
curl -s -X POST $BASE/groups/$GROUP/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Dinner","amount":300.00,"paid_by":"Alice","category":"food"}'

# 3b. Equal split among a subset
curl -s -X POST $BASE/groups/$GROUP/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Snacks","amount":20.00,"paid_by":"Bob","split_type":"EQUAL",
       "splits":[{"participant":"Alice"},{"participant":"Bob"}]}'

# 3c. Exact amounts (must total the expense amount)
curl -s -X POST $BASE/groups/$GROUP/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Taxi","amount":90.00,"paid_by":"Bob","category":"transport","split_type":"EXACT",
       "splits":[{"participant":"Alice","value":30.00},{"participant":"Carol","value":60.00}]}'

# 3d. Percentages (must total 100)
curl -s -X POST $BASE/groups/$GROUP/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Hotel","amount":1000.00,"paid_by":"Carol","category":"accommodation","split_type":"PERCENTAGE",
       "splits":[{"participant":"Alice","value":50},{"participant":"Bob","value":30},{"participant":"Carol","value":20}]}'

# 4. List expenses
curl -s $BASE/groups/$GROUP/expenses

# 5. Settlement summary
curl -s $BASE/groups/$GROUP/settlement

# 6. Record a payment (Carol pays Alice), then list payments
curl -s -X POST $BASE/groups/$GROUP/payments -H 'Content-Type: application/json' \
  -d '{"from":"Carol","to":"Alice","amount":160.00,"note":"cash"}'
curl -s $BASE/groups/$GROUP/payments
```

### Settlement response (shape)

```json
{
  "group_id": "…",
  "group_name": "Bali Trip",
  "total_expenses": 390.00,
  "service_charge_pct": 2,
  "service_charge_amount": 7.80,
  "settled": false,
  "balances": [
    {"participant": "Alice", "paid": 300.00, "owed": 130.00, "payments_made": 0.00, "payments_received": 0.00, "net": 170.00}
  ],
  "transfers": [
    {"from": "Carol", "to": "Alice", "amount": 160.00},
    {"from": "Bob", "to": "Alice", "amount": 10.00}
  ],
  "category_summary": {"food": 300.00, "transport": 90.00}
}
```

`net > 0` means the group owes that person; `net < 0` means they owe the group.

## Design notes

- **Facts, not balances, are stored.** Only immutable expenses and payments are persisted. Balances are
  derived on every read, so there is one source of truth and nothing to drift out of sync.
- **No lost cents.** Equal and percentage splits use the largest-remainder method on whole cents, so
  shares always sum exactly to the expense amount (`100.00 / 3 -> 33.34, 33.33, 33.33`). Inputs with more
  than 2 decimals are rejected rather than silently rounded.
- **Settlement minimisation.** Largest debtor pays largest creditor. Each transfer fully clears at least
  one person, so there are at most `n - 1` transfers, and debt chains collapse (A owes B, B owes C -> A pays C).
- **Payments are bounded.** A payment may not exceed what the payer still owes or what the receiver is
  still owed, which keeps balances consistent and prevents accidental reversal of debts.
- **Concurrency.** Mutations on a group are serialised with a per-group lock, so the check-then-write in
  payments cannot race.
- **Audit trail.** Every expense and payment carries a `created_at` timestamp from an injectable `Clock`.

## Tests

`mvn test` runs unit tests for the split, settlement and service-charge logic (including randomised
property-style checks that settlements fully clear balances and that splits never lose a cent) and a
MockMvc integration test covering the full create -> expense -> settle -> pay flow and error cases.
