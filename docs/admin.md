# Admin access — LabelMate

There is no public "Register as Admin" flow, and no API assigns roles.
Admin status is granted directly in the database.

## Granting ADMIN to an existing user (development / operations)

1. Register (or identify) the normal account first.
2. Update its role in MySQL:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

3. The user signs out and back in (a fresh login picks up the role), then
   opens `/admin`.

## Rules

- Never expose role assignment to normal users.
- The frontend Admin link is UX only; every `/api/admin/**` endpoint
  re-checks the persisted role server-side (non-admins get 403).
- Admin responses never include password hashes.
