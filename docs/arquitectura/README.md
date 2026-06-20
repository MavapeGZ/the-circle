# Modelo de dominio del backend

Este documento resume las entidades principales del backend por microservicio y las relaciones que existen entre ellas. En este proyecto las relaciones entre microservicios son, en general, logicas y no JPA: se modelan con IDs y llamadas entre servicios.

## Vista global

```mermaid
graph LR
	Users[ms-users]
	Catalog[ms-catalog]
	Contracts[ms-contracts]
	Gamification[ms-gamification]
	Notifications[ms-notifications]

	Users --> Catalog
	Users --> Contracts
	Users --> Gamification
	Catalog --> Contracts
	Contracts --> Gamification
	Contracts --> Notifications
	Users --> Notifications
```

## ms-users

Responsable de la identidad, el perfil publico y la informacion sensible del usuario.

### Entidades

```mermaid
erDiagram
	USER ||--o{ KNOWN_DEVICE : owns

	USER {
		Long id PK
		String email
		String password
		String firstName
		String lastName
		String address
		String zone
		String idNumber
		String profilePicture
		String ibanEncrypted
		String ibanLast4
		KycStatus kycStatus
		boolean emailVerified
		boolean marketingEmailsOptIn
		boolean systemEmailsOptIn
		LocalDateTime deletedAt
		Boolean legacyKycVerified
		String role
		LocalDateTime createdAt
	}

	KNOWN_DEVICE {
		Long id PK
		Long userId
		String deviceToken
		String userAgent
		LocalDateTime createdAt
		LocalDateTime lastSeenAt
	}
```

### Relaciones

- `User` 1 -> N `KnownDevice` mediante `KnownDevice.userId`.
- `profilePicture` almacena solo el nombre de archivo del avatar y no una URL completa.
- `KycStatus` es un enum del ciclo de verificacion del usuario: `UNVERIFIED`, `PENDING_REVIEW`, `VERIFIED`, `REJECTED`.
- `legacyKycVerified` existe por compatibilidad con datos antiguos y se sincroniza con `kycStatus`.
- `zone` es una clasificacion cerrada reutilizada por otros microservicios, pero no existe una FK fisica con `ms-catalog` o `ms-contracts`.

## ms-catalog

Gestiona el catalogo de articulos publicado por los usuarios.

### Entidades

```mermaid
erDiagram
	ARTICLE {
		String id PK
		String title
		String description
		ProductType productType
		ArticleStatus status
		String category
		Double price
		Double guaranteeAmount
		Long authorId
		String zone
		Instant createdAt
		String imageBase64
	}
```

### Relaciones

- `Article.authorId` apunta de forma logica a `ms-users.User.id`.
- `Article.zone` se copia desde `ms-users` al publicar para evitar manipulacion desde el cliente.
- `Article.status` se usa para el ciclo de vida del articulo: `AVAILABLE`, `RESERVED`, `SOLD`, `DELETED`.
- `ProductType` clasifica el articulo: `DONATION`, `SYMBOLIC_RENTAL`, `SYMBOLIC_SALE`, `DEMAND`.
- `ms-contracts` actualiza el estado del articulo via endpoint interno cuando un contrato se crea o se firma.

## ms-contracts

Modela el contrato de negocio, los pagos simulados y el flujo de firma.

### Entidades

```mermaid
erDiagram
	CONTRACT ||--o{ PAYMENT : has
	CONTRACT ||--o{ SIGNATURE_SESSION : opens
	CONTRACT ||--o{ SIGNATURE_RECORD : traces
	CONTRACT ||--o{ STORED_CONTRACT : renders
	STORED_CONTRACT ||--o{ SIGNATURE_RECORD : archives

	CONTRACT {
		String id PK
		String itemId
		String ownerId
		String receiverId
		ContractType type
		ContractStatus status
		BigDecimal price
		BigDecimal guaranteeAmount
		GuaranteeStatus guaranteeStatus
		String conditions
		LocalDateTime returnDate
		LocalDateTime createdAt
		LocalDateTime signedAt
		LocalDateTime receiverSignedAt
		LocalDateTime ownerSignedAt
		String storedContractId
	}

	PAYMENT {
		String id PK
		String contractId
		String payerUserId
		String payeeUserId
		BigDecimal amount
		String currency
		PaymentStatus status
		String cardLast4
		String cardBrand
		String payoutIbanLast4
		LocalDateTime simulatedAt
		LocalDateTime escrowExpiresAt
		LocalDateTime releasedAt
		LocalDateTime refundedAt
		String failureReason
	}

	STORED_CONTRACT {
		String id PK
		String filename
		String contractId
		byte[] data
		LocalDateTime createdAt
		String signerEmail
		String clientIp
		String userAgent
	}

	SIGNATURE_SESSION {
		String id PK
		String contractId
		String signerKey
		String signerEmail
		String signerFullName
		String otpHash
		LocalDateTime expiresAt
		int attempts
		SignatureSessionStatus status
		byte[] payloadPdf
		String contractJson
		String visualOptionsJson
		String preHash
		LocalDateTime createdAt
	}

	SIGNATURE_RECORD {
		String id PK
		String storedContractId
		String contractId
		String signerKey
		String signerFullName
		String signerIdNumber
		String signerEmail
		LocalDateTime signedAtUtc
		String ip
		String userAgent
		String otpSessionId
		String preHash
		String postHash
		String algorithm
	}
```

### Relaciones

- `Contract.itemId` apunta de forma logica a `ms-catalog.Article.id`.
- `Contract.ownerId`, `Contract.receiverId`, `Payment.payerUserId` y `Payment.payeeUserId` referencian usuarios de `ms-users` por ID, sin FK real.
- `Contract` 1 -> N `Payment` mediante `Payment.contractId`.
- `Contract` 1 -> N `SignatureSession` mediante `SignatureSession.contractId`.
- `Contract` 1 -> N `SignatureRecord` mediante `SignatureRecord.contractId`.
- `StoredContract` guarda el PDF generado y se enlaza con `Contract` por `storedContractId` y con auditoria mediante `SignatureRecord.storedContractId`.
- `SignatureSessionStatus` controla el flujo de OTP: `PENDING`, `USED`, `EXPIRED`, `LOCKED`.
- `Contract`, `Payment` y `StoredContract` forman el nucleo del flujo economico y documental del microservicio.

## ms-gamification

Gestiona puntos, insignias y el historial de transacciones de gamificacion.

### Entidades

```mermaid
erDiagram
	BADGE ||--o{ USER_BADGE : awards
	USER_POINTS ||--o{ POINT_TRANSACTION : records

	BADGE {
		Long id PK
		String code
		String name
		String description
		String iconUrl
		String tier
		Integer requiredPoints
		EventType requiredEventType
		Integer requiredEventCount
	}

	USER_POINTS {
		Long id PK
		Long userId
		int totalPoints
		LocalDateTime updatedAt
		Long version
	}

	USER_BADGE {
		Long id PK
		Long userId
		Long badgeId FK
		LocalDateTime awardedAt
	}

	POINT_TRANSACTION {
		Long id PK
		Long userId
		int points
		EventType eventType
		String referenceId
		LocalDateTime createdAt
	}
```

### Relaciones

- `UserPoints` mantiene un unico registro por `userId`.
- `UserBadge.userId` apunta al usuario externo y `UserBadge.badge` referencia a `Badge` como relacion JPA interna.
- `PointTransaction.userId` guarda el historial de puntos por usuario.
- `EventType` define los eventos que otorgan puntos: `ITEM_DONATED`, `ITEM_RENTED_SOLIDARITY`, `CONTRACT_SIGNED`, `REVIEW_RECEIVED`, `PROFILE_COMPLETED`.
- Las vistas publicas de usuario en `ms-users` consumen este microservicio para mostrar resumen de puntos y badges.

## ms-notifications

Registra el resultado del envio de emails.

### Entidades

```mermaid
erDiagram
	EMAIL_LOG {
		Long id PK
		String toAddress
		String subject
		String templateName
		EmailStatus status
		String errorMessage
		Instant sentAt
		Instant createdAt
	}
```

### Relaciones

- No hay relaciones JPA con otras entidades dentro de este microservicio.
- `EmailLog` funciona como bitacora tecnica del envio, con `EmailStatus` en `QUEUED`, `SENT` o `FAILED`.

## Resumen de relaciones entre microservicios

- `ms-users` es la fuente de verdad de identidad y perfil.
- `ms-catalog` publica articulos y depende de `ms-users` para obtener zona y autor.
- `ms-contracts` consume `ms-catalog` para el articulo y `ms-users` para firmantes, direcciones e IBAN en los PDFs y pagos.
- `ms-gamification` consume eventos de negocio de `ms-contracts` y otros hitos de usuario para calcular puntos y badges.
- `ms-notifications` almacena trazas del envio de emails; su informacion operativa se alimenta desde los casos de uso del resto de servicios.
# Modelo de dominio del backend

Este documento resume las entidades principales del backend por microservicio y las relaciones que existen entre ellas. En este proyecto las relaciones entre microservicios son, en general, logicas y no JPA: se modelan con IDs y llamadas entre servicios.

## Vista global

```mermaid
graph LR
	Users[ms-users]
	Catalog[ms-catalog]
	Contracts[ms-contracts]
	Gamification[ms-gamification]
	Notifications[ms-notifications]

	Users --> Catalog
	Users --> Contracts
	Users --> Gamification
	Catalog --> Contracts
	Contracts --> Gamification
	Contracts --> Notifications
	Users --> Notifications
```

## ms-users

Responsable de la identidad, el perfil publico y la informacion sensible del usuario.

### Entidades

```mermaid
erDiagram
	USER ||--o{ KNOWN_DEVICE : owns

	USER {
		Long id PK
		String email
		String password
		String firstName
		String lastName
		String address
		String zone
		String idNumber
		String ibanEncrypted
		String ibanLast4
		KycStatus kycStatus
		boolean emailVerified
		boolean marketingEmailsOptIn
		boolean systemEmailsOptIn
		LocalDateTime deletedAt
		String role
		LocalDateTime createdAt
	}

	KNOWN_DEVICE {
		Long id PK
		Long userId
		String deviceToken
		String userAgent
		LocalDateTime createdAt
		LocalDateTime lastSeenAt
	}
```

### Relaciones

- `User` 1 -> N `KnownDevice` mediante `KnownDevice.userId`.
- `KycStatus` es un enum del ciclo de verificacion del usuario: `UNVERIFIED`, `PENDING_REVIEW`, `VERIFIED`, `REJECTED`.
- `zone` es una clasificacion cerrada reutilizada por otros microservicios, pero no existe una FK fisica con `ms-catalog` o `ms-contracts`.

## ms-catalog

Gestiona el catalogo de articulos publicado por los usuarios.

### Entidades

```mermaid
erDiagram
	ARTICLE {
		String id PK
		String title
		String description
		ProductType productType
		ArticleStatus status
		String category
		Double price
		Double guaranteeAmount
		Long authorId
		String zone
		Instant createdAt
		String imageBase64
	}
```

### Relaciones

- `Article.authorId` apunta de forma logica a `ms-users.User.id`.
- `Article.zone` se copia desde `ms-users` al publicar para evitar manipulacion desde el cliente.
- `Article.status` se usa para el ciclo de vida del articulo: `AVAILABLE`, `RESERVED`, `SOLD`, `DELETED`.
- `ProductType` clasifica el articulo: `DONATION`, `SYMBOLIC_RENTAL`, `SYMBOLIC_SALE`, `DEMAND`.
- `ms-contracts` actualiza el estado del articulo via endpoint interno cuando un contrato se crea o se firma.

## ms-contracts

Modela el contrato de negocio, los pagos simulados y el flujo de firma.

### Entidades

```mermaid
erDiagram
	CONTRACT ||--o{ PAYMENT : has
	CONTRACT ||--o{ SIGNATURE_SESSION : opens
	CONTRACT ||--o{ SIGNATURE_RECORD : traces
	CONTRACT ||--o{ STORED_CONTRACT : renders
	STORED_CONTRACT ||--o{ SIGNATURE_RECORD : archives

	CONTRACT {
		String id PK
		String itemId
		String ownerId
		String receiverId
		ContractType type
		ContractStatus status
		BigDecimal price
		BigDecimal guaranteeAmount
		GuaranteeStatus guaranteeStatus
		String conditions
		LocalDateTime returnDate
		LocalDateTime createdAt
		LocalDateTime signedAt
		LocalDateTime receiverSignedAt
		LocalDateTime ownerSignedAt
		String storedContractId
	}

	PAYMENT {
		String id PK
		String contractId
		String payerUserId
		String payeeUserId
		BigDecimal amount
		String currency
		PaymentStatus status
		String cardLast4
		String cardBrand
		String payoutIbanLast4
		LocalDateTime simulatedAt
		LocalDateTime escrowExpiresAt
		LocalDateTime releasedAt
		LocalDateTime refundedAt
		String failureReason
	}

	STORED_CONTRACT {
		String id PK
		String filename
		String contractId
		byte[] data
		LocalDateTime createdAt
		String signerEmail
		String clientIp
		String userAgent
	}

	SIGNATURE_SESSION {
		String id PK
		String contractId
		String signerKey
		String signerEmail
		String signerFullName
		String otpHash
		LocalDateTime expiresAt
		int attempts
		SignatureSessionStatus status
		byte[] payloadPdf
		String contractJson
		String visualOptionsJson
		String preHash
		LocalDateTime createdAt
	}

	SIGNATURE_RECORD {
		String id PK
		String storedContractId
		String contractId
		String signerKey
		String signerFullName
		String signerIdNumber
		String signerEmail
		LocalDateTime signedAtUtc
		String ip
		String userAgent
		String otpSessionId
		String preHash
		String postHash
		String algorithm
	}
```

### Relaciones

- `Contract.itemId` apunta de forma logica a `ms-catalog.Article.id`.
- `Contract.ownerId`, `Contract.receiverId`, `Payment.payerUserId` y `Payment.payeeUserId` referencian usuarios de `ms-users` por ID, sin FK real.
- `Contract` 1 -> N `Payment` mediante `Payment.contractId`.
- `Contract` 1 -> N `SignatureSession` mediante `SignatureSession.contractId`.
- `Contract` 1 -> N `SignatureRecord` mediante `SignatureRecord.contractId`.
- `StoredContract` guarda el PDF generado y se enlaza con `Contract` por `storedContractId` y con auditoria mediante `SignatureRecord.storedContractId`.
- `SignatureSessionStatus` controla el flujo de OTP: `PENDING`, `USED`, `EXPIRED`, `LOCKED`.
- `Contract`, `Payment` y `StoredContract` forman el nucleo del flujo economico y documental del microservicio.

## ms-gamification

Gestiona puntos, insignias y el historial de transacciones de gamificacion.

### Entidades

```mermaid
erDiagram
	BADGE ||--o{ USER_BADGE : awards
	USER_POINTS ||--o{ POINT_TRANSACTION : records

	BADGE {
		Long id PK
		String code
		String name
		String description
		String iconUrl
		String tier
		Integer requiredPoints
		EventType requiredEventType
		Integer requiredEventCount
	}

	USER_POINTS {
		Long id PK
		Long userId
		int totalPoints
		LocalDateTime updatedAt
		Long version
	}

	USER_BADGE {
		Long id PK
		Long userId
		Long badgeId FK
		LocalDateTime awardedAt
	}

	POINT_TRANSACTION {
		Long id PK
		Long userId
		int points
		EventType eventType
		String referenceId
		LocalDateTime createdAt
	}
```

### Relaciones

- `UserPoints` mantiene un unico registro por `userId`.
- `UserBadge.userId` apunta al usuario externo y `UserBadge.badge` referencia a `Badge` como relacion JPA interna.
- `PointTransaction.userId` guarda el historial de puntos por usuario.
- `EventType` define los eventos que otorgan puntos: `ITEM_DONATED`, `ITEM_RENTED_SOLIDARITY`, `CONTRACT_SIGNED`, `REVIEW_RECEIVED`, `PROFILE_COMPLETED`.
- Las vistas publicas de usuario en `ms-users` consumen este microservicio para mostrar resumen de puntos y badges.

## ms-notifications

Registra el resultado del envio de emails.

### Entidades

```mermaid
erDiagram
	EMAIL_LOG {
		Long id PK
		String toAddress
		String subject
		String templateName
		EmailStatus status
		String errorMessage
		Instant sentAt
		Instant createdAt
	}
```

### Relaciones

- No hay relaciones JPA con otras entidades dentro de este microservicio.
- `EmailLog` funciona como bitacora tecnica del envio, con `EmailStatus` en `QUEUED`, `SENT` o `FAILED`.

## Resumen de relaciones entre microservicios

- `ms-users` es la fuente de verdad de identidad y perfil.
- `ms-catalog` publica articulos y depende de `ms-users` para obtener zona y autor.
- `ms-contracts` consume `ms-catalog` para el articulo y `ms-users` para firmantes, direcciones e IBAN en los PDFs y pagos.
- `ms-gamification` consume eventos de negocio de `ms-contracts` y otros hitos de usuario para calcular puntos y badges.
- `ms-notifications` almacena trazas del envio de emails; su informacion operativa se alimenta desde los casos de uso del resto de servicios.
