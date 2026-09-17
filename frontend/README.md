# NovaCRM Admin Frontend

React/Vite admin dashboard implemented from the supplied Figma Make design. The dashboard calls the Spring Boot API directly; no mock dataset is bundled.

## Run

```powershell
npm install
$env:VITE_API_BASE_URL='http://localhost:8080'
npm run dev
```

The default API base URL is `http://localhost:8080`. Admin list screens call `/api/admin/users`, `/products`, `/orders`, `/feedback`, and `/surveys`; dashboard cards call the revenue, user, and survey report endpoints.

## Planned screens

- Login and password management
- Admin dashboard
- Customer management
- Feedback management
- Survey management and results
- Customer portal

Add the frontend package configuration and source code here when implementation starts.
