# Anh Lớn shop — Cửa hàng thời trang nam

React/Vite customer storefront for men's clothing. The UI is Vietnamese throughout and uses the sequence-diagram APIs directly; no mock product dataset is bundled in the frontend.

## Run

```powershell
npm install
$env:VITE_API_BASE_URL='http://localhost:8082'
npm run dev
```

The default API base URL is `http://localhost:8082`. Admin list screens call `/api/admin/users`, `/products`, `/orders`, `/feedback`, and `/surveys`; dashboard cards call the revenue, user, and survey report endpoints.

## Implemented customer flows

- Product catalog with keyword search, category, price and sort filters
- Product detail with size/color selection, verified-purchase feedback and ratings
- Guest cart plus persisted customer cart
- Customer registration, login, refresh-token session and logout
- Profile and style-preference update
- COD and payOS checkout-link flows
- Order history and delivery-status tracking
- Published style surveys and one-response protection
