# payOS Promo Code Checkout Sequence Design

## Goal

Extend the customer checkout sequence so an optional promotion code is validated and applied by the backend before creating a pending order and requesting payment from payOS.

## Scope

- Update `docs/diagrams/customer/III-07-shopping-cart-order-payos.puml` only.
- Add an optional `promoCode` to the order request.
- Show backend validation of the promotion's validity period, eligibility conditions, and remaining usage limit.
- Show server-side calculation of `subtotal`, `discountAmount`, and `finalAmount`.
- Show persistence of the promotion code and calculated amounts with the order.
- Show payOS receiving `finalAmount` as its payment amount.
- Show webhook verification that the received amount exactly matches the stored `finalAmount`.
- Keep all existing stock reservation, return URL, webhook idempotency, failure handling, and timeout cleanup flows.

## Design

### Trusted calculation boundary

The customer portal sends only `promoCode` as an optional input. The backend does not trust client-provided discount or final amount values. It calculates all monetary values after loading the cart and before creating the pending order.

### Promotion validation and usage

Inside the existing checkout transaction, the backend validates that the code exists, is active, is within its validity period, satisfies the order conditions, and has remaining usage capacity. When accepted, the backend reserves one usage so concurrent orders cannot consume the same last available use. The order stores the code, discount amount, and final amount.

If checkout cannot continue because the code is invalid or not applicable, the API returns a client error and does not create the order. If payment fails, is cancelled, expires, or is cleaned up, the reserved usage is released. If payment succeeds, the usage is confirmed.

### payOS and webhook verification

The payment request sends `amount = finalAmount`. The webhook verifies the signature, order identity, and that the received amount equals the order's stored `finalAmount`. A mismatch is rejected and does not transition the order to `CONFIRMED` or `PAID`.

### Sequence outcomes

| Situation | Result |
|---|---|
| No promo code | `discountAmount = 0`; payOS receives the calculated order total |
| Valid promo code | Code and discount are stored; payOS receives `finalAmount` |
| Invalid, expired, ineligible, or exhausted code | Checkout returns an error; no order is created |
| Webhook amount mismatch | Webhook is rejected; order/payment remains unconfirmed |
| Payment failure or timeout after reservation | Order is cancelled/expired and promo usage is released |
| Successful payment | Order is confirmed and promo usage is finalized |

## Acceptance criteria

- The request line contains `promoCode` as an optional field.
- The sequence visibly includes promotion validation, discount calculation, final amount calculation, and order persistence.
- The payOS request visibly sends `finalAmount`, not the pre-discount total.
- The webhook visibly compares the received amount with the stored `finalAmount` and rejects mismatches.
- Failure, duplicate webhook, and timeout cleanup paths remain present.
- The diagram contains no raw SQL commands; database operations use business-language actions.
