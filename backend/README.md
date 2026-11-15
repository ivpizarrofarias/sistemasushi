# Backend — Sushi Local (Sistema de caja)

API REST del sistema de punto de venta. Paquete base `cl.ipfsoftware.backend`,
clase principal `BackendApplication`, artefacto Maven `backend`
(`backend-1.0.0.jar`), puerto por defecto **8080**.

Este README describe las **funcionalidades del backend**: módulos, endpoints,
roles, reglas de negocio, modelo de datos y configuración.
La descripción general del proyecto está en [`../README.md`](../README.md).

## 1. Stack

- **Java 17**, **Spring Boot 4.1.2**, Maven wrapper 3.9.13.
- `spring-boot-starter-webmvc` (Tomcat 11), `spring-boot-starter-data-jpa`
  (Hibernate ORM 7.4.13), `spring-boot-starter-validation`,
  `spring-boot-starter-security` (sesión por cookie `JSESSIONID`, BCrypt).
- **PostgreSQL** (driver 42.7.14) como base productiva; **H2** solo para tests
  (perfil `test`, sin tocar PostgreSQL).
- **Apache POI 5.3.0** (`poi-ooxml`) para generar el Excel `.xlsx`.
- **Lombok 1.18.48**, `spring-boot-devtools`.

## 2. Arranque y configuración

```powershell
.\mvnw.cmd -B clean test     # tests de contexto con H2
.\mvnw.cmd spring-boot:run   # desarrollo (http://localhost:8080)
```

`src/main/resources/application.properties`:

> Las credenciales de PostgreSQL no se versionan: define `DB_URL`,
> `DB_USER` y `DB_PASSWORD` en el entorno (o en un `.env` para Docker Compose).

| Propiedad | Valor / variable |
|---|---|
| `server.port` | `8080` (`SERVER_PORT`) |
| Sesión | `8h`, `SameSite=Lax`, `HttpOnly`, `JSESSIONID` |
| `spring.datasource.url` | `${DB_URL}` (obligatoria) |
| `spring.datasource.username` | `${DB_USER}` (obligatoria) |
| `spring.datasource.password` | `${DB_PASSWORD}` (obligatoria) |
| `spring.jpa.hibernate.ddl-auto` | `update` (`JPA_DDL_AUTO`) |
| Zona horaria | `America/Santiago` (JPA + Jackson) |
| `app.cors.allowed-origins` | `http://localhost:5173,http://127.0.0.1:5173` (`CORS_ORIGINS`) |
| `app.business.name` / `app.currency` | `Sushi Local` / `CLP` |

Sin sesión, la API responde `401 {"message":"Debes iniciar sesión"}`.
Sin permiso, `403 {"message":"No tienes permisos para realizar esta acción"}`.
Los errores devuelven `{"message": "...", "details": [...]}`.

### Datos iniciales (`DataSeeder`)

Si la tabla `app_users` está vacía, crea las cuentas definidas en las
variables de entorno `SEED_ADMIN_USERNAME` y `SEED_ADMIN_PASSWORD`
(opcionalmente `SEED_CASHIER_USERNAME` y `SEED_CASHIER_PASSWORD`). No hay
credenciales por defecto en el código.

Y si no hay productos, crea 4 categorías (**Rolls**, **Combos**, **Bebidas**,
**Postres**) y 9 productos de ejemplo (rolls, combos, bebidas y postres).
Las contraseñas se guardan hasheadas con BCrypt.

## 3. Autenticación, sesiones y roles

- Login custom: `POST /api/auth/login` → guarda la autenticación en la
  **sesión HTTP** (`HttpSessionSecurityContextRepository`). Invalida la sesión
  anterior al entrar (protección contra fijación de sesión).
- El **login no distingue mayúsculas** (`Admin` entra como `admin`).
- Logout: `POST /api/auth/logout` (invalida la sesión y borra `JSESSIONID`).
- CSRF deshabilitado (la sesión se usa con `IF_REQUIRED`); CORS solo permite
  `localhost:5173` con credenciales.

| Rol | Acceso |
|---|---|
| **ADMIN** | Todo: usuarios, inventario (movimientos), escritura de productos/categorías, anular ventas, reportes y Excel. |
| **CASHIER** | Login, venta, caja, listado de productos/categorías y reportes (incluye Excel). **No** puede ver Usuarios ni anular ventas. |

## 4. Endpoints (`/api/...`)

Base: `http://localhost:8080`. Todos requieren sesión salvo
`POST /api/auth/login`.

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/login` | Público | Inicia sesión y crea la cookie `JSESSIONID`. Cuerpo: `{"username","password"}`. |
| `GET` | `/api/auth/me` | Autenticado | Usuario actual (nombre, rol, activo). |
| `POST` | `/api/auth/logout` | Autenticado | Cierra la sesión. |

### Categorías — `/api/categories`

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/categories` | Autenticado | Lista ordenada por nombre. |
| `POST` | `/api/categories` | ADMIN | Crea categoría (nombre único sin distinguir mayúsculas). |
| `PUT` | `/api/categories/{id}` | ADMIN | Actualiza nombre/color. |
| `DELETE` | `/api/categories/{id}` | ADMIN | Desactiva la categoría (baja lógica). |

### Productos — `/api/products` (lectura: autenticado; escritura: ADMIN)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/products?activeOnly=true` | Autenticado | Lista; opcional `categoryId`. |
| `GET` | `/api/products/{id}` | Autenticado | Detalle de un producto. |
| `POST` | `/api/products` → `201` | ADMIN | Crea producto (nombre único, precio > 0, stock ≥ 0). |
| `PUT` | `/api/products/{id}` | ADMIN | Actualiza producto. Si cambia el stock registra un movimiento `ADJUSTMENT` con el usuario actual. |
| `PATCH` | `/api/products/{id}/restock` | ADMIN | Suma stock y registra movimiento `RESTOCK` con motivo. |
| `DELETE` | `/api/products/{id}` → `204` | ADMIN | Desactiva el producto (baja lógica). |

### Inventario — `/api/inventory` (ADMIN)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/inventory/movements` | ADMIN | Últimos 50 movimientos (tipo, cantidad, stock anterior/nuevo, motivo, usuario, fecha). |

### Caja — `/api/cash`

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/cash/current` | Autenticado | Sesión de caja abierta o `null` (esperado, ventas en efectivo, total). |
| `POST` | `/api/cash/open` | Autenticado | Abre caja con `openingAmount`. Falla si ya hay una abierta. |
| `POST` | `/api/cash/close` | Autenticado | Cierra: guarda `closingAmount`, calcula `expected = inicial + ventas en efectivo`, diferencia y notas. |
| `GET` | `/api/cash/sessions` | Autenticado | Últimas 20 sesiones (historial). |

### Ventas — `/api/sales`

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `POST` | `/api/sales` → `201` | Autenticado | **Registra una venta.** Exige caja abierta. Bloquea productos con `PESSIMISTIC_WRITE` ordenados por id (sin sobreventa ni deadlocks), descuenta stock, calcula subtotal/descuento/total/vuelto y guarda el folio `V-000001`, `V-000002`, … |
| `GET` | `/api/sales?date=2026-11-09` | Autenticado | Ventas de una fecha (por defecto hoy, zona América/Santiago), en orden descendente. |
| `GET` | `/api/sales/{id}` | Autenticado | Detalle de la venta. |
| `POST` | `/api/sales/{id}/void` | ADMIN | Anula la venta (`VOIDED`), restaura el stock y registra movimientos `SALE_VOID`. |

Reglas de venta: producto activo, stock suficiente; descuento ≤ subtotal;
si es efectivo, `amountReceived ≥ total`. El folio se genera a partir del
id (`V-%06d`). La **boleta PDF** la genera el frontend (jsPDF) a partir de
la venta creada.

### Reportes — `/api/reports` (cualquier usuario autenticado)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/reports?from=&to=` | Autenticado | Resumen del período: `total`, `count`, `totalUnits`, `cashTotal`, `cardTotal`, `transferTotal`, desglose por medio de pago y `topProducts` (producto, cantidad, ingreso, máx. 10). Sin parámetros usa el día de hoy. |
| `GET` | `/api/reports/excel?from=&to=` | Autenticado | Descarga el **`.xlsx`** (Apache POI). Nombre `ventas-del-dia-{fecha}.xlsx` si `from==to`, si no `reporte-ventas-{from}_a_{to}.xlsx`. Hojas: **Resumen** (totales del período), **Productos** (producto, cantidad, total) y **Ventas** (número, fecha, hora, cajero, pago, total, estado, productos). |

### Usuarios — `/api/users` (ADMIN)

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `GET` | `/api/users` | ADMIN | Lista de usuarios. |
| `POST` | `/api/users` → `201` | ADMIN | Crea usuario (username único sin distinguir mayúsculas, contraseña obligatoria). |
| `PUT` | `/api/users/{id}` | ADMIN | Actualiza usuario. Bloquea desactivar/degradar al **último administrador activo**. |

## 5. Reglas de negocio importantes

1. **Caja obligatoria para vender.** No hay caja abierta → `409`.
2. **Stock consistente:** venta y anulación bloquean las filas de productos
   (`findByIdForUpdate`, `PESSIMISTIC_WRITE`) en orden de id; así se evita
   sobreventa y deadlocks concurrentes.
3. **Anular restaura stock** y deja movimientos `SALE_VOID` por producto.
4. **Editar stock a mano** registra `ADJUSTMENT` con el usuario que lo hizo.
5. **Nombres sin duplicados:** productos, categorías y usuarios se validan
   `IgnoreCase` + `trim`; el login también es case-insensitive.
6. **Último admin protegido:** no se puede desactivar ni cambiar el rol del
   último `ADMIN` activo.
7. **Reportes excluyen anuladas:** solo cuentan ventas `COMPLETED`;
   los productos top se agrupan por `id` para no duplicar nombres renombrados.
8. **Excel generado en el servidor** con Apache POI (3 hojas, totales y
   formatos de moneda CLP).

## 6. Modelo de datos (PostgreSQL)

Hibernate `update` crea estas tablas (entidades JPA en `cl.ipfsoftware.backend.domain`):

| Tabla | Entidad | Claves / notas |
|---|---|---|
| `app_users` | `User` | `username` único, `password` BCrypt, `full_name`, `role` (`ADMIN`/`CASHIER`), `active`. |
| `categories` | `Category` | `name` único, `color`, `active`. |
| `products` | `Product` | `name`, `price`, `stock`, `min_stock`, FK `category_id`, `active`. |
| `sales` | `Sale` | `number` (`V-000001`), FK `cashier_id`, `cash_session_id`, `payment_method` (`CASH`/`DEBIT`/`CREDIT`/`TRANSFER`), `status` (`COMPLETED`/`VOIDED`), `subtotal`, `discount`, `total`, `amount_received`, `change_amount`. |
| `sale_items` | `SaleItem` | FK `sale_id`, FK `product_id` (nullable), `product_name`, `unit_price`, `quantity`, `line_total`. |
| `cash_sessions` | `CashSession` | `status` (`OPEN`/`CLOSED`), FK `opened_by`/`closed_by`, `opening_amount`, `closing_amount`, `expected_amount`, `difference`, `opened_at`, `closed_at`, `notes`. |
| `stock_movements` | `StockMovement` | FK `product_id`, `type` (`SALE`/`SALE_VOID`/`RESTOCK`/`ADJUSTMENT`), `quantity`, `previous_stock`, `new_stock`, `reason`, FK `user_id`, `created_at`. |

## 7. Estructura de paquetes

```text
cl.ipfsoftware.backend
├─ BackendApplication            clase principal
├─ config/      SecurityConfig, DataSeeder
├─ domain/      User, Role, Category, Product, Sale, SaleItem, PaymentMethod,
│               SaleStatus, CashSession(+Status), StockMovement(+Type)
├─ repository/  User, Category, Product, Sale, CashSession, StockMovement
├─ service/     User, Category, Product, Cash, Sale, Report, ReportExport
├─ dto/         requests + responses (records), ApiError
└─ web/         Auth, User, Category, Product, Inventory, Cash, Sale, Report
                controllers + ApiExceptionHandler
```

DTOs con `record` y cálculo de vistas (`SaleDto.from(...)`). Las respuestas de
error usan `{"message","details"}` (400 validación, 401 sin sesión,
403 sin permiso, 404 no encontrado, 409 conflicto).

## 8. Tests

```powershell
.\mvnw.cmd -B clean test
```

Usan **H2 en memoria** (`src/test/resources/application-test.properties`,
perfil `test`) y no requieren PostgreSQL. El test de contexto
`BackendApplicationTests` levanta Spring con todos los repositorios.
