# Story NOTIF-01 — WebSocket Infrastructure
**Epic:** Epic 8 — Notifications | **Points:** 4 SP | **Status:** Not Started

## Description
`WebSocketEventPublisher` — typed event publisher for all STOMP topics; `WebSocketConfig` with SockJS fallback at `ws://{host}/ws`

## Acceptance Criteria
- [ ] `WebSocketConfig` configures STOMP over WebSocket at `/ws` endpoint with SockJS fallback
- [ ] Message broker configured for `/topic` (broadcast) and `/queue` (personal) channels
- [ ] `WebSocketEventPublisher` provides typed `publish(topic, payload)` method
- [ ] JWT authentication validated on STOMP CONNECT (via `ChannelInterceptor`)
- [ ] Connection rejected with 401 if JWT invalid or missing
- [ ] Tenant ID extracted from JWT and stored in STOMP session for scoping

## Technical Notes
Spring WebSocket + STOMP. `SimpMessagingTemplate` wrapped in `WebSocketEventPublisher`. Security via `ChannelInterceptor`.
