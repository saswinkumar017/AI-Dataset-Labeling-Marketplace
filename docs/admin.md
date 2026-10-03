# Admin access — LabelMate

There is no public "Register as Admin" flow: registration always creates
`ANNOTATOR`. Admin status is granted by an existing admin in the panel.

## Creating an admin (panel)

1. Register (or identify) the normal account first.
2. As an admin, open `/admin/users`, search the account, click **Make admin**.
3. The user signs out and back in (fresh login picks up the role).

Guards (server-side, `AdminService.updateUserRole`):

- Only `ADMIN` callers can change roles (persisted role re-checked, 403 else).
- No self role-change.
- The last remaining admin cannot be demoted.
- No user DELETE in the panel (protects owned datasets/projects FKs).

## First-ever admin (bootstrap, no admin exists yet)

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

Sign out/in, open `/admin`.

## AI settings (`/admin/settings`)

- `GET/PUT /api/admin/ai-settings` (ADMIN only).
- Key returned masked (`****last4`), never raw; blank/masked on save keeps it.
- Stored in `app_settings` table. Key, model and on/off apply live;
  base URL and chat backend (`openai`/`none`) need a backend restart.
- Status pill in the panel reports live state: `live` means the Spring AI
  bean is up AND enabled AND a key is configured.
- One-time setup: `APP_AI_ENABLED=true` and `SPRING_AI_MODEL_CHAT=openai`
  in `server/.env` plus one restart, so the AI bean exists; after that the
  panel owns the key, model and on/off with no restarts.
- Never logged, never in responses raw.

## Rules

- Never expose role assignment to normal users.
- The frontend Admin link is UX only; every `/api/admin/**` endpoint
  re-checks the persisted role server-side (non-admins get 403).
- Admin responses never include password hashes or raw API keys.
