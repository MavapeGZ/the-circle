# The Circle — Documento Técnico-Funcional

> Plataforma web de economía circular solidaria (compra, venta, alquiler y donación de
> bienes) con verificación de identidad, contratos digitales firmados con OTP y
> mensajería entre usuarios. Proyecto de TFM. Este documento describe **qué hace** la
> aplicación y **cómo** lo hace, con foco técnico en las partes críticas (login, firma
> OTP, chat, pagos en garantía).

Índice:

1. Visión general y objetivos
2. Arquitectura
3. Stack tecnológico
4. Modelo de datos por servicio
5. API Gateway y comunicación entre servicios
6. Catálogo de funcionalidades
7. Flujos críticos en detalle
   - 7.1 Autenticación y sesiones
   - 7.2 Verificación de identidad (KYC)
   - 7.3 Firma electrónica de contratos con OTP
   - 7.4 Ciclo de vida del contrato
   - 7.5 Pagos y depósito en garantía (escrow simulado)
   - 7.6 Chat en (casi) tiempo real
   - 7.7 Gamificación y reseñas
8. Seguridad transversal
9. Internacionalización (i18n) y notificaciones
10. Despliegue
11. Limitaciones conocidas

---

## 1. Visión general y objetivos

**The Circle** permite publicar **ofertas** (venta simbólica, alquiler simbólico,
donación) y **demandas** de productos, priorizando a personas con bajos recursos o en
riesgo de exclusión. Para garantizar un entorno seguro —especialmente para quien dona o
presta— la plataforma integra:

- **Verificación de identidad oficial (KYC)** en el onboarding.
- **Generación automática de contratos digitales (PDF)** firmados mediante **OTP por
  email** (firma electrónica avanzada).
- **Pago en garantía (escrow)** simulado para ventas y depósitos de alquiler.
- **Mensajería** entre dueño del artículo e interesado.
- **Gamificación** (puntos e insignias) para incentivar la solidaridad.

Reglas de negocio destacadas: los precios de venta/alquiler simbólico están **topados a
10 €**; las donaciones y demandas son siempre **gratuitas**; el depósito de garantía de
alquiler está topado a **20 €** y lo fija el dueño al publicar (el comprador no puede
manipularlo).

---

## 2. Arquitectura

Arquitectura de **microservicios** sobre Spring Boot, con un frontend SPA en React. Todo
el tráfico de cliente entra por un **API Gateway** (Spring Cloud Gateway).

```
                         ┌─────────────────────────────┐
   Navegador (SPA React) │  Vite dev :5173 / Vercel    │
        │                └─────────────────────────────┘
        │ HTTPS  Authorization: Bearer <JWT>  + cookies httpOnly
        ▼
┌───────────────────────────────────────────────────────────────┐
│ api-gateway  :8080   (Spring Cloud Gateway, CORS, enrutado)    │
└───────────────────────────────────────────────────────────────┘
   │ /api/auth, /api/users   │ /api/catalog   │ /api/contracts,    │ /api/gamification  │ /api/notifications
   ▼                         ▼                │ /api/chat          ▼                    ▼
┌────────────┐   ┌────────────┐   ┌────────────────┐   ┌────────────────┐   ┌──────────────────┐
│ ms-users   │   │ ms-catalog │   │ ms-contracts   │   │ ms-gamification│   │ ms-notifications │
│  :8081     │   │  :8082     │   │  :8083         │   │  :8084         │   │  :8085           │
│ auth, KYC, │   │ artículos, │   │ contratos,     │   │ puntos,        │   │ envío de emails  │
│ perfiles,  │   │ búsqueda   │   │ firma OTP,     │   │ insignias,     │   │ (SMTP) +         │
│ reviews,   │   │ OpenSearch │   │ pagos, chat,   │   │ ranking        │   │ plantillas HTML  │
│ devices    │   │            │   │ PDF            │   │                │   │                  │
└─────┬──────┘   └─────┬──────┘   └───────┬────────┘   └───────┬────────┘   └────────┬─────────┘
      │ PostgreSQL     │ OpenSearch       │ PostgreSQL         │ PostgreSQL          │ PostgreSQL
      │ users_db       │ (índice)         │ contracts_db       │ gamification_db     │ notifications_db
      ▼                ▼                  ▼                    ▼                     ▼
   ┌──────────────────────────────────────────────────────────────────────────────────┐
   │ PostgreSQL 15 (una instancia, varias bases) │ OpenSearch 2.11 (catálogo)            │
   └──────────────────────────────────────────────────────────────────────────────────┘
```

Principios:

- **Una base de datos por servicio** (esquema "database per service"). PostgreSQL aloja
  `users_db`, `contracts_db`, `gamification_db`, `notifications_db`; el catálogo NO usa
  SQL sino **OpenSearch** como almacén/buscador.
- **Comunicación síncrona vía HTTP/REST**. No hay broker de mensajería: las llamadas
  entre servicios son `RestClient`/HTTP directas.
- **El gateway solo enruta** `/api/**`. Los endpoints internos (`/internal/**`) **no**
  están enrutados por el gateway, por lo que no son alcanzables desde Internet, y además
  van protegidos por una clave compartida (ver §5).

---

## 3. Stack tecnológico

| Capa | Tecnología |
| :--- | :--- |
| Frontend | React + Vite, React Router, axios, react-i18next, TailwindCSS |
| Gateway | Spring Cloud Gateway (reactivo, Netty) |
| Microservicios | Java + Spring Boot (Spring Web, Security, Data JPA) |
| BBDD relacional | PostgreSQL 15 (`users`, `contracts`, `gamification`, `notifications`) |
| Búsqueda/catálogo | OpenSearch 2.11 (Spring Data Elasticsearch/OpenSearch) |
| PDF | Apache PDFBox (render + firma visual) |
| Auth | JWT (HS256, jjwt), BCrypt, cookies httpOnly |
| Email | Spring Mail (SMTP) + plantillas Thymeleaf/HTML |
| Contenedores | Docker + Docker Compose (prod: Caddy + DuckDNS) |

JVM ajustada para entornos modestos: `-Xmx256m -XX:+UseSerialGC` por servicio.

---

## 4. Modelo de datos por servicio

**ms-users (`users_db`)**
- `User`: nombre, email (único, normalizado a minúsculas), password (BCrypt), `role`,
  `kycStatus` (UNVERIFIED → PENDING_REVIEW → VERIFIED/REJECTED), `emailVerified`,
  dirección, nº documento, `zone`, `language`, IBAN cifrado + `ibanLast4`, avatar.
- `RefreshToken`: solo el **hash SHA-256** del token, `userId`, expiración.
- `KnownDevice`: dispositivos de confianza (token HMAC), userAgent, `lastSeenAt`.
- `Review`: reseña entre usuarios (rating + comentario), ligada a un contrato concluido.

**ms-catalog (OpenSearch)**
- `Article`: título, descripción, precio, categoría, `productType`
  (SYMBOLIC_SALE, SYMBOLIC_RENTAL, DONATION, DEMAND), `guaranteeAmount` (solo alquiler),
  `status` (AVAILABLE, RESERVED, SOLD, DELETED), `authorId`, `zone`, imagen Base64.

**ms-contracts (`contracts_db`)**
- `Contract`: `itemId`, `ownerId`, `receiverId`, `type` (SALE/RENT/DONATION/...),
  `status` (ver §7.4), `price`, `guaranteeAmount`, `guaranteeStatus`, marcas de tiempo de
  firma (`ownerSignedAt`/`receiverSignedAt`), de entrega (`ownerDeliveredAt`/
  `receiverReceivedAt`), `storedContractId`.
- `StoredContract`: PDF firmado (bytes) + **auditoría** (email firmante, IP, userAgent,
  fecha). Id tipo nanoid de 21 chars.
- `Payment`: pago simulado en escrow (estado, importe, últimos 4 de tarjeta y de IBAN,
  caducidad de escrow).
- `Conversation` / `Message`: chat (ver §7.6).
- `SignatureSession` / `SignatureRecord`: evidencia de firma.

**ms-gamification (`gamification_db`)**: `UserPoints`, `PointTransaction`, `Badge`,
`UserBadge`.

**ms-notifications (`notifications_db`)**: `EmailLog` (estado de cada envío).

---

## 5. API Gateway y comunicación entre servicios

**Enrutado** (`api-gateway/application.yml`), por prefijo de ruta:

| Ruta pública | Servicio destino |
| :--- | :--- |
| `/api/auth/**`, `/api/users/**` | ms-users :8081 |
| `/api/catalog/**` | ms-catalog :8082 |
| `/api/contracts/**`, `/api/chat/**` | ms-contracts :8083 |
| `/api/gamification/**` | ms-gamification :8084 |
| `/api/notifications/**` | ms-notifications :8085 |

**CORS**: configurado globalmente en el gateway. Orígenes permitidos vía
`CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173`), `allowCredentials: true`
para que viajen las cookies httpOnly.

**Dos mecanismos de autenticación entre piezas:**

1. **JWT de usuario propagado.** El token `Authorization: Bearer` viaja del navegador al
   gateway y de ahí al microservicio. Cada servicio que necesita identificar al usuario
   **verifica el JWT por sí mismo** contra el secreto compartido `JWT_SECRET` (HS256). Por
   ejemplo, `ms-contracts` no está autenticado por el gateway: usa su propio
   `JwtAuthService.requireUserId()` para validar firma/caducidad y extraer la claim
   `userId`, con la que autoriza el acceso a un contrato (debe ser `ownerId` o
   `receiverId`).

2. **Clave interna compartida** (`X-Internal-Api-Key`) para llamadas servicio-a-servicio
   a endpoints `/internal/**` que exponen **PII** (p. ej. `ms-catalog`/`ms-contracts`
   piden a `ms-users` la identidad para rellenar el PDF, o la presencia de IBAN para el
   pago). Estos endpoints **no se enrutan por el gateway**. La guarda **falla cerrada**:
   si la clave está vacía fuera de los perfiles `local`/`dev`, el servicio **se niega a
   arrancar** (no deja PII desprotegida); en dev una clave vacía solo emite un warning.

Claves por servicio: `USERS_INTERNAL_KEY`, `CATALOG_INTERNAL_KEY`,
`CONTRACTS_INTERNAL_KEY`, `NOTIFICATIONS_INTERNAL_KEY`.

---

## 6. Catálogo de funcionalidades

- **Registro y login** con verificación de email por OTP y segundo factor por OTP en
  dispositivos no confiables (§7.1).
- **Recuperación de contraseña** por OTP + token de reset de un solo uso ligado a IP.
- **Verificación KYC** subiendo anverso/reverso del documento (§7.2).
- **Perfil público** con reseñas, insignias y puntos; ajustes (idioma, moneda, IBAN de
  cobro, avatar, dispositivos de confianza, borrado de cuenta).
- **Publicación de artículos** (oferta/demanda) con tipo, precio, zona, imagen, depósito.
- **Catálogo y búsqueda** con OpenSearch: búsqueda difusa (fuzzy) multi-campo, filtros por
  tipo de producto y zona, paginación; oculta artículos SOLD/DELETED.
- **Contratos digitales** generados como PDF, firmados con OTP por ambas partes (§7.3-7.4).
- **Pagos / escrow** simulado para ventas con precio y depósitos de alquiler (§7.5).
- **Chat** por artículo entre dueño e interesado (§7.6).
- **Gamificación**: puntos por donar/alquilar/firmar/reseñar/completar perfil + insignias
  + ranking (§7.7).
- **Reseñas** bidireccionales tras concluir un trato.
- **Notificaciones por email** (verificación, OTP login, reset, OTP de firma) localizadas.
- **i18n** ES/EN en frontend y backend; importes mostrados en la moneda elegida.

Rutas SPA principales (React Router): `/`, `/catalog`, `/catalog/:id`, `/login`,
`/register`, `/forgot-password`, y protegidas `/create`, `/contracts`,
`/contracts/:id`, `/contracts/:id/sign`, `/contracts/:id/checkout`, `/messages`,
`/messages/:conversationId`, `/profile`, `/settings`.

---

## 7. Flujos críticos en detalle

### 7.1 Autenticación y sesiones

La sesión combina un **access JWT** de vida corta y un **refresh token** opaco de vida
larga, con **confianza de dispositivo** para decidir cuándo pedir OTP de login.

**Tokens**

- **Access JWT** (HS256): `subject` = email, claims `userId`, `kyc_verified`,
  `email_verified`, `exp`. TTL por defecto **1 hora** (`JWT_EXPIRATION`). Se guarda en
  `localStorage` y se envía como `Authorization: Bearer`. Cada servicio lo verifica
  localmente contra `JWT_SECRET`.
- **Refresh token** opaco: se guarda **solo como hash SHA-256** en `refresh_tokens` y se
  entrega al navegador en cookie **httpOnly** `tc_refresh` (path `/api/auth`). TTL por
  defecto **30 días**. **Rota en cada uso**: cada refresh borra el presentado y emite uno
  nuevo (detección de reuso).
- **Cookie de dispositivo** `tc_device` (httpOnly): token `{randomId}.{HMAC-SHA256(randomId|userId)}`
  persistido en `known_devices`. Marca un dispositivo como de confianza.

**Registro (signup) + verificación de email**

1. `POST /api/auth/register`: se normaliza el email (trim+lowercase), se comprueba
   unicidad, se crea el `User` con `emailVerified=false`, password BCrypt, `kycStatus=UNVERIFIED`.
2. Se emite un **OTP de 6 dígitos** (`AuthOtpService`, en memoria) con TTL 600 s y máx. 5
   intentos; se guarda **hasheado con BCrypt**. Se envía por email (plantilla
   `account-verification`). La respuesta devuelve `sessionId` (no hay sesión todavía).
3. `POST /api/auth/verify-email` con `{sessionId, otp}`: se consume el OTP (un solo uso).
   Como ese dispositivo acaba de probar control del buzón, se **marca de confianza**
   (`tc_device`), de modo que un login posterior desde él se salta el OTP. Se devuelven
   access JWT + cookie `tc_refresh`.

**Login**

```
POST /api/auth/login {email, password}
  └─ AuthenticationManager valida credenciales (BadCredentials → 401 genérico)
     ├─ email NO verificado  → reenvía OTP de verificación, responde {sessionId, requiresEmailVerification}
     ├─ dispositivo de confianza (cookie tc_device válida) → mintea sesión directa {token} + tc_refresh
     └─ dispositivo nuevo     → emite OTP de LOGIN por email, responde {sessionId, requiresOtp}
                                  └─ POST /api/auth/login-otp {sessionId, otp}
                                       → consume OTP, marca dispositivo de confianza,
                                         devuelve {token} + tc_device + tc_refresh
```

La validación de la cookie de dispositivo recalcula el HMAC y lo compara en **tiempo
constante**, y exige además que el token exista en `known_devices` para ese `userId`.

**Renovación silenciosa (refresh)**

El TTL de 1 h del access token **ya no limita la sesión**. El interceptor de axios:

- Inyecta el `Bearer` en cada petición.
- Ante un **401** en una petición normal (no `/auth/*`), llama **una vez** a
  `POST /api/auth/refresh` (que usa la cookie `tc_refresh`), guarda el nuevo access token
  y **reintenta** la petición original. Las 401 concurrentes comparten **una sola**
  llamada de refresh en vuelo (`refreshPromise`) para no disparar N rotaciones (cada
  rotación invalida la anterior).
- Si el refresh falla (token ausente/expirado/ya usado), limpia la sesión y redirige a
  `/login?expired=1`.

Servidor: `AuthService.refresh()` → `RefreshTokenService.rotate()` valida el hash,
borra el token presentado, emite uno nuevo y devuelve access JWT + nuevo refresh. La
sesión efectiva dura, por tanto, lo que el refresh token (30 días por defecto).

**Logout y revocaciones**

- `POST /api/auth/logout`: revoca el refresh token presentado y limpia su cookie
  (siempre 200, incluso con cookie ausente). El frontend limpia el estado local aunque
  la llamada falle.
- **Reset de contraseña** y **borrado de cuenta** revocan **todos** los refresh tokens y
  **todos** los dispositivos de confianza del usuario, cerrando cualquier sesión activa.

**Recuperación de contraseña** (resistente a enumeración de cuentas)

1. `POST /api/auth/forgot-password`: **rate-limit por IP** (429 compartido, no distingue
   email conocido/desconocido). Siempre responde 200 con un `sessionId` y mensaje neutro;
   si el email no existe se devuelve un UUID falso sin OTP detrás. El envío SMTP se hace
   **fuera** de la ruta de timing y los errores se silencian, para que la latencia/forma
   de la respuesta no sea un canal lateral.
2. `POST /api/auth/verify-reset-otp`: valida el OTP y emite un **token de reset de un solo
   uso**, con TTL corto y **ligado a la IP** de origen.
3. `POST /api/auth/reset-password`: consume el token (debe venir de la misma IP), aplica
   la nueva contraseña (BCrypt, mínimo 6 chars) y **revoca todas las sesiones y
   dispositivos**.

**Nota de escalado:** los OTP de auth viven **en memoria** (`ConcurrentHashMap`):
single-instance. Para escalar ms-users horizontalmente habría que moverlos a Redis.

### 7.2 Verificación de identidad (KYC)

`POST /api/users/{id}/kyc` (multipart `front`+`back`). `KycService`:

1. Guarda los ficheros bajo `KYC_UPLOAD_DIR/{userId}/` con **nombre saneado** (solo
   `[a-zA-Z0-9._-]`, sin `/`, `\` ni `..`; protección anti *path traversal* verificando
   que la ruta resuelta queda dentro del directorio del usuario).
2. Estado → `PENDING_REVIEW` (y de paso marca el email como verificado si no lo estaba).
3. Llama al **proveedor de validación** (`KycValidationService`). La implementación actual
   es `MockKycProvider`: comprueba presencia, tamaño ≤ 5 MB y extensión
   (png/jpg/jpeg/pdf) y acepta. En producción se sustituiría por un proveedor real.
4. Si valida → `VERIFIED` y se **reemite el JWT** (con `kyc_verified=true`) para que el
   cliente refleje el nuevo estado sin re-login. Si falla → borra ficheros y `REJECTED`.
   Ante excepción, restaura el estado original (transaccionalidad lógica).

### 7.3 Firma electrónica de contratos con OTP

Firma electrónica **avanzada** basada en OTP por email, en `ms-contracts`
(`SignatureWorkflowService`). Cada parte (comprador/receiver y dueño/owner) firma por
separado; el contrato pasa a ACTIVE solo cuando **ambas** han firmado.

**Paso 1 — solicitar OTP**: `POST /api/contracts/.../sign/request` con
`{signerEmail, signerRole, contract, visualOptions, signatureMode}`.

- Valida cuerpo y que `signatureMode` sea `ADVANCED`.
- Genera un OTP de 6 dígitos con `SecureRandom`, lo **hashea con BCrypt** y crea una
  **sesión en memoria** (`ConcurrentHashMap`) con: hash, expiración (`ttl-seconds`=600),
  email, rol, snapshot del contrato y opciones visuales.
- Envía el OTP por email (canal `OtpDeliveryChannel`; en dev puede exponerse en la
  respuesta con `expose-in-response`). Si el envío falla, **elimina la sesión** y
  devuelve 502.
- Responde `{sessionId}`.

**Paso 2 — confirmar OTP y firmar**: `POST /api/contracts/.../sign/confirm` con
`{sessionId, otp}` (+ IP y userAgent del request).

Comprobaciones en orden (cada una con su código HTTP):

| Situación | Código |
| :--- | :--- |
| `sessionId`/`otp` ausente | 400 |
| sesión no encontrada/expirada de mapa | 404 |
| sesión ya usada | 409 |
| OTP caducado (TTL) | 410 |
| más de `max-attempts` (5) intentos | 429 |
| OTP incorrecto (`BCrypt.matches`) | 401 |

Si el OTP es correcto:

1. **Reclama la sesión** (`claim()` sincronizado) **antes** de cualquier efecto, para que
   dos confirmaciones concurrentes con el mismo OTP válido no produzcan dos firmas. Si
   falla algo después, hace `release()` para permitir reintento con el OTP aún vigente.
2. **Enriquece** los datos de firmantes pidiendo a `ms-users` (nombre, dirección, nº doc)
   para que el PDF no sea anónimo, reutilizando esos perfiles para resolver el idioma.
3. **Genera el PDF** (`ContractPdfService`) en el idioma del firmante.
4. **Aplica la firma visual** (`SignatureService` con PDFBox): dibuja la imagen de firma
   (Base64) o, en su defecto, texto en cursiva con el nombre, en las coordenadas/página
   indicadas en `visualOptions`, más un pie con el nombre completo.
5. **Persiste** el PDF firmado con **auditoría** (`StoredContractService.saveWithAudit`:
   email, IP, userAgent, timestamp).
6. **Registra la firma de esa parte** (`ContractService.markSigned`) enlazando el PDF. El
   contrato pasa a ACTIVE solo si han firmado **ambas** partes (y, si requiere pago, tras
   liberar el escrow — ver §7.5).
7. Si queda totalmente firmado, **otorga el evento de gamificación** (donación/alquiler/
   venta) en *best-effort* (nunca rompe la firma) y devuelve las insignias ganadas para
   que la UI las muestre.

**Verificación**: `GET /api/contracts/verify/{storedContractId}` confirma la existencia y
metadatos del PDF firmado.

**Limpieza**: un `@Scheduled` purga sesiones OTP expiradas cada ~2 min para no filtrar
memoria.

> **Escalado:** igual que los OTP de auth, las sesiones de firma están **en memoria** →
> single-instance (una confirmación servida por otro nodo no encontraría la sesión).

### 7.4 Ciclo de vida del contrato

Estados (`ContractStatus`): `DRAFT → PENDING_SIGNATURES → (AWAITING_COUNTERPARTY) →
ACTIVE → DELIVERED / COMPLETED`, con `CANCELLED` como rama terminal.

```
crear (abrir pantalla de firma)
   │  idempotente: reusa PENDING sin firmar del mismo item/owner/receiver/tipo
   ▼
PENDING_SIGNATURES ──1ª firma──▶ artículo RESERVED en catálogo
   │
   ├─ requiere pago (venta con precio / alquiler con depósito):
   │     ambas firmas + escrow liberado ──▶ ACTIVE (artículo SOLD)
   │     ambas firmas pero sin pagar     ──▶ AWAITING_COUNTERPARTY
   │
   └─ no requiere pago (donación/cesión/venta a 0):
         ambas firmas ──▶ ACTIVE (artículo SOLD)

ACTIVE ──ambas partes confirman entrega──▶ DELIVERED  (habilita reseñas)
ACTIVE (alquiler con depósito) ──devolución del depósito──▶ COMPLETED
escrow caducado sin firmar la contraparte ──▶ CANCELLED (reembolso, artículo AVAILABLE)
```

Detalles clave:

- **No se reserva el artículo al crear** el contrato (abrir la pantalla de firma no
  bloquea el item); la reserva ocurre en la **primera firma**. Un `@Scheduled` **purga
  contratos PENDING nunca firmados** pasadas 72 h (`pending-ttl-hours`), evitando filas
  muertas y artículos reservados colgados.
- La notificación a `ms-catalog` (RESERVED/SOLD) se ejecuta **después del commit** de la
  transacción (`afterCommit`), para no mantener locks de fila durante una llamada HTTP
  entre servicios. Un fallo al marcar SOLD se registra a nivel ERROR (reconciliación
  manual: el item quedaría visible pese a estar vendido).
- **Confirmación de entrega** (`confirmDelivery`): owner confirma entregado, receiver
  confirma recibido; updates **por columna atómicos** para que las dos confirmaciones
  concurrentes no se pisen; al estar ambas, → DELIVERED. Solo válido sobre contratos
  ACTIVE. Los alquileres con depósito **no** usan este handshake: se liquidan devolviendo
  el depósito.
- **Depósito de garantía** (`GuaranteeStatus`): `NONE → DEPOSITED → RELEASED/CLAIMED`. El
  dueño **devuelve** (RELEASED) o **retiene** (CLAIMED) el depósito; en ambos casos el
  contrato pasa a COMPLETED.
- **Borrado de cuenta**: elimina los contratos *abiertos* del usuario como dueño con sus
  pagos/PDFs/evidencias y libera los artículos (AVAILABLE); conserva COMPLETED/CANCELLED/
  DELIVERED como histórico y los contratos donde es receiver.

### 7.5 Pagos y depósito en garantía (escrow simulado)

`PaymentService` simula un **escrow**: no hay PSP ni dinero real; la fila existe para la
auditoría y la UX. Solo se persisten los **últimos 4** de tarjeta e IBAN.

```
receiver paga ─▶ ESCROWED, contrato → AWAITING_COUNTERPARTY
   │              (fondos retenidos hasta que el owner firme)
   ├─ owner firma dentro de la ventana ─▶ RELEASED, contrato → ACTIVE
   └─ owner NO firma antes de escrowExpiresAt (7 días) ─▶ REFUNDED, contrato → CANCELLED
                                                          (barrido @Scheduled cada 10 min)
```

`POST /api/contracts/{id}/pay`:

- **Autorización**: solo el `receiverId` (comprador) puede pagar (verificado con la claim
  `userId` del JWT).
- Solo en estados que admiten pago, y solo SALE/RENT con importe > 0.
- Importe: precio para venta, depósito de garantía para alquiler (valor canónico de la
  fila, no manipulable por el cliente).
- **Validación de tarjeta**: normalización, **Luhn**, caducidad no pasada, CVC. Tarjeta
  demo terminada en `0000` → fallo simulado (`FAILED`).
- Exige que el vendedor tenga **IBAN de cobro** configurado (consulta interna a ms-users;
  el IBAN en claro nunca sale de ms-users, solo `hasIban` + últimos 4).
- Crea el pago `ESCROWED` con `escrowExpiresAt = now + 7 días`.

`releaseEscrowOnDualSign` (llamado desde la firma cuando ambas partes han firmado) libera
el pago en escrow (idempotente). El barrido `refundExpiredEscrows` reembolsa los escrows
caducados y cancela el contrato (liberando el artículo).

### 7.6 Chat en (casi) tiempo real

Mensajería 1-a-1 **por artículo** entre el dueño y un interesado. **No usa WebSocket/SSE**:
el "tiempo real" se aproxima con **polling** desde el frontend.

**Backend** (`ChatService`, expuesto en `/api/chat/**`):

- `startOrGet(callerId, articleId)`: resuelve el artículo en catálogo (HTTP), fija
  `ownerId` = autor del artículo y `initiatorId` = quien escribe. El autor **no puede**
  abrir conversación sobre su propio artículo. Restricción de unicidad
  `(articleId, initiatorId)`; una carrera (doble clic) se captura por
  `DataIntegrityViolationException` y se relee. *No es @Transactional* a propósito: no
  debe mantener una conexión/transacción de BBDD durante el round-trip HTTP al catálogo.
- `listForUser`: inbox del usuario ordenado por `lastMessageAt` desc; calcula preview del
  último mensaje y contador de no leídos en **2 consultas batch** (evita N+1).
- `getMessages`: solo participantes; **marca como leídos** los mensajes dirigidos al
  caller y devuelve el hilo ordenado.
- `sendMessage`: solo participantes; persiste el mensaje y actualiza `lastMessageAt`.
- `deleteAllForUser`: borra conversaciones y mensajes al eliminar la cuenta.

**Frontend** (`Messages.jsx`): dos `setInterval` a **5 s** (`POLL_MS`) — uno refresca la
lista de conversaciones y otro el hilo abierto. La barra de navegación (`App.jsx`)
**sondea cada 15 s** el total de no leídos para el badge. Esto es sencillo y robusto pero
añade latencia (hasta 5 s) y carga de polling; un WebSocket/SSE sería el siguiente paso.

### 7.7 Gamificación y reseñas

**Gamificación** (`GamificationService`). Eventos y puntos:

| Evento | Puntos |
| :--- | :--- |
| `ITEM_DONATED` | 50 |
| `ITEM_RENTED_SOLIDARITY` | 30 |
| `CONTRACT_SIGNED` | 20 |
| `REVIEW_RECEIVED` | 10 |
| `PROFILE_COMPLETED` | 25 |

- `processEvent` es **idempotente** por `(userId, eventType, referenceId)`: un evento ya
  procesado es no-op. Reintenta hasta 4 veces ante conflictos de escritura concurrente
  (`OptimisticLockingFailureException` / `DataIntegrityViolationException`), invocándose a
  sí mismo vía proxy Spring para que cada intento corra en su propia transacción.
- Tras sumar puntos, evalúa **insignias** (`Badge`): por umbral de puntos
  (`requiredPoints`) o por recuento de eventos de un tipo (`requiredEventType` +
  `requiredEventCount`). Devuelve las recién ganadas para que la UI las muestre (toast).
- Ofrece **resumen** de usuario (puntos + insignias + transacciones recientes), resúmenes
  batch y **ranking** (top 10).

**Reseñas** (`ms-users`): un usuario reseña a la contraparte tras un trato concluido. El
derecho a reseñar se **gatea** consultando a ms-contracts si el contrato es *reviewable*
(entrega confirmada en venta/donación, o depósito liquidado en alquiler). Una reseña
recibida dispara el evento `REVIEW_RECEIVED`.

---

## 8. Seguridad transversal

- **Contraseñas y OTP**: BCrypt. Los OTP nunca se guardan en claro; se comparan con
  `BCrypt.matches`. OTP de 6 dígitos generados con `SecureRandom`, TTL 600 s, máx. 5
  intentos, un solo uso.
- **JWT**: HS256 con secreto compartido; el `JwtAuthenticationFilter` (ms-users) y el
  `JwtAuthService` (ms-contracts) tragan tokens malformados/expirados y dejan la petición
  sin autenticar (→ 401 limpio en vez de 500).
- **Cookies httpOnly**: `tc_refresh` (path `/api/auth`), `tc_device` y reset; flags
  `Secure` activables por entorno (`REFRESH_COOKIE_SECURE`, `DEVICE_COOKIE_SECURE`).
- **Refresh token**: almacenado solo como hash SHA-256, **rotación en cada uso**.
- **Cookie de dispositivo**: HMAC-SHA256 comparado en **tiempo constante** + verificación
  en BBDD.
- **Resistencia a enumeración** en `forgot-password` (respuesta neutra, timing constante,
  rate-limit por IP, `X-Forwarded-For` solo si se confía explícitamente en el proxy).
- **Reset token**: un solo uso, TTL corto, ligado a la IP de origen.
- **Endpoints internos**: fuera del gateway + clave `X-Internal-Api-Key`, *fail-closed*
  fuera de dev (se niega a arrancar sin clave).
- **PII minimizada en tránsito**: el IBAN en claro no sale de ms-users; solo `hasIban` y
  últimos 4. Los PDFs (con PII) requieren ser parte del contrato (owner/receiver).
- **Anti path-traversal** en subidas KYC; validación de tamaño/extensión.
- **Manipulación de importes**: depósito de alquiler tomado del catálogo (lo fija el
  dueño, topado a 20 €), ignorando el valor del payload del comprador; precios simbólicos
  topados a 10 € y donaciones/demandas forzadas a 0 en el catálogo.
- **Validación de tarjeta**: Luhn + caducidad + CVC (simulado, sin PAN persistido).

---

## 9. Internacionalización (i18n) y notificaciones

- **Frontend**: react-i18next con `messages_es`/`messages_en`. Existe contexto de
  **preferencias** (idioma + moneda); los importes se formatean en la **moneda elegida**
  por el usuario.
- **Backend**: cada servicio relevante tiene `messages*.properties`. La auth envía
  **claves de asunto** (`email.subject.verify`, `.login`, `.reset`) y `ms-notifications`
  resuelve el asunto localizado según el idioma del destinatario.
- **ms-notifications**: `POST /api/notifications/...` (o llamada interna) recibe
  destinatario, clave de asunto, nombre de plantilla, idioma y variables; renderiza la
  plantilla HTML (`account-verification`, `login-otp`, `otp`, `password-reset`), envía por
  SMTP (config `MAIL_*`) de forma **asíncrona** y registra el resultado en `EmailLog`.

---

## 10. Despliegue

**Local (`docker-compose.yml`)**: cada microservicio en su contenedor con puerto
publicado (8080–8085); PostgreSQL :5432 (con `init.sql` que crea las bases) y OpenSearch
:9200. El frontend Vite (:5173) ataca al gateway en `http://localhost:8080/api`.

Variables mínimas (`.env`): `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, y las claves internas
`*_INTERNAL_KEY`. Opcionales de auth: `JWT_EXPIRATION` (1 h), `JWT_REFRESH_EXPIRATION`
(30 d), `REFRESH_COOKIE_MAX_AGE_DAYS`, `*_COOKIE_SECURE`. SMTP: `MAIL_HOST/PORT/USER/
PASSWORD/FROM`.

Arranque:

```bash
docker compose up -d           # backend + infra
cd frontend && npm install && npm run dev
```

**Producción (`docker-compose.prod.yml`)**: añade **Caddy** (TLS automático) + **DuckDNS**
como reverse proxy/HTTPS frente al gateway. Recordatorio: OpenSearch exige
`vm.max_map_count=262144` en el host (Linux/WSL2).

---

## 11. Limitaciones conocidas

- **Estado en memoria single-instance**: OTP de auth (ms-users) y sesiones de firma
  (ms-contracts) viven en `ConcurrentHashMap`. No se puede escalar horizontalmente esos
  servicios sin mover el estado a un almacén compartido (Redis). Reinicio = se pierden las
  sesiones pendientes.
- **Chat por polling** (5 s): latencia y carga; sin WebSocket/SSE.
- **Pagos simulados**: no hay PSP real; el escrow es lógico (auditoría/UX).
- **KYC mock**: `MockKycProvider` solo valida formato/tamaño; no comprueba el documento de
  verdad.
- **Consistencia entre servicios best-effort**: las transiciones de catálogo (RESERVED/
  SOLD/AVAILABLE) son HTTP post-commit sin reintento automático; un fallo deja el estado
  para reconciliación manual (registrado a nivel ERROR).
- **Secreto JWT compartido** entre servicios (HS256): práctico para un TFM; una rotación
  o un esquema asimétrico (RS256) reduciría el radio de impacto ante fuga.

---

*Documento generado a partir del estado del código en la rama `main`.*
