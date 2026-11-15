# Requerimientos del sistema — Sushi Local

> Documento de requerimientos elaborado a partir de la revisión del código fuente
> (backend, frontend, configuración y despliegue) al **15-11-2026**.
> Describe el comportamiento **realmente implementado** y los límites explícitos
> del sistema. Complementa al `README.md` desde la perspectiva funcional.

---

## 1. Información del documento

| Campo | Valor |
|---|---|
| Producto | **Sushi Local — Sistema de caja (POS)** |
| Tipo de documento | Requerimientos funcionales y no funcionales |
| Versión | 1.0 |
| Fecha | 2026-11-15 |
| Estado | Vigente |
| Fuente de verdad | Código del repositorio (backend + frontend) |
| Audiencia | Dueño del local, administradores, desarrolladores y soporte |

### 1.1 Propósito

Definir qué debe hacer el sistema, bajo qué reglas, con qué roles y qué queda
explícitamente fuera de su alcance. Sirve como referencia para operar, capacitar,
probar y planificar mejoras sin asumir funciones que el software no tiene.

### 1.2 Alcance

El sistema cubre la operación de **venta en mostrador / para llevar** de un local
de sushi:

- Inicio y cierre de sesión de usuarios.
- Venta de productos con control de stock.
- Apertura, cierre y arqueo de caja por turno.
- Administración de productos, categorías e inventario.
- Reportes de ventas y exportación a Excel.
- Generación de una boleta en PDF (comprobante interno).
- Administración de usuarios y roles.

No cubre mesas, delivery, facturación tributaria, pagos en línea, compras a
proveedores, multi-sucursal ni contabilidad. El detalle está en la sección 11.

---

## 2. Definiciones

| Término | Significado |
|---|---|
| **POS** | Punto de venta; pantalla donde se arma el carrito y se cobra. |
| **Caja / sesión de caja** | Turno de operación con un monto inicial, ventas y un cierre con arqueo. |
| **Arqueo** | Comparación entre el efectivo esperado y el efectivo contado al cerrar caja. |
| **Venta completada** | Venta registrada y vigente (`COMPLETED`). |
| **Venta anulada** | Venta revertida (`VOIDED`); deja de sumar en reportes y restaura stock. |
| **Stock mínimo** | Umbral configurable por producto para mostrar alerta de stock bajo. |
| **Folio** | Número correlativo interno de la venta, con formato `V-000001`. |
| **Boleta** | PDF de 80 mm generado en el navegador. Es un comprobante interno **sin valor tributario**. |
| **ADMIN** | Rol administrador; acceso total. |
| **CASHIER** | Rol cajero; venta y caja, sin administración. |
| **CLP** | Peso chileno; moneda única del sistema. |
| **IVA** | Impuesto al valor agregado. Se muestra referencialmente al 19 %. |

---

## 3. Actores y roles

| Actor | Descripción | Acceso |
|---|---|---|
| **Administrador (`ADMIN`)** | Dueño o encargado. Configura el negocio y supervisa. | Todo el sistema. |
| **Cajero (`CASHIER`)** | Atiende el mostrador y opera la caja. | Venta, caja, consulta de productos/categorías, reportes. |
| **Sistema / proceso automático** | Tareas internas: seed inicial, numeración de ventas, movimientos de stock, cálculo de totales. | Sin interfaz. |
| **Operador de infraestructura** | Instala, configura y respalda el servidor y la base de datos. | Fuera de la aplicación (SSH, Docker, PostgreSQL). |

> El sistema **no** tiene un rol de supervisor, auditor, contador ni cliente.
> Solo existen dos roles: `ADMIN` y `CASHIER`.

---

## 4. Requerimientos funcionales

La columna **Estado** indica si la función está disponible en la versión actual.
Todos los requerimientos listados como *Implementado* están presentes en el código.

### 4.1 Autenticación y sesión

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-AUT-01 | Inicio de sesión | El usuario ingresa nombre de usuario y contraseña. El nombre **no distingue mayúsculas/minúsculas**. Estado: implementado. |
| RF-AUT-02 | Validación de credenciales | La contraseña se verifica con BCrypt. Si el usuario no existe, está inactivo o la contraseña es incorrecta, se responde con error genérico. Estado: implementado. |
| RF-AUT-03 | Sesión web | Se crea una sesión con cookie `JSESSIONID`, `HttpOnly`, `SameSite=Strict`, duración de **2 horas**. Estado: implementado. |
| RF-AUT-04 | Cierre de sesión | El usuario puede cerrar sesión; se invalida la sesión y se elimina la cookie. Estado: implementado. |
| RF-AUT-05 | Protección de fijación de sesión | Al autenticarse se descarta cualquier sesión previa y se crea una nueva. Estado: implementado. |
| RF-AUT-06 | Límite de intentos | Máximo **5 intentos por IP cada 10 minutos** sobre `POST /api/auth/login`; al exceder responde HTTP 429. Estado: implementado (ver limitación L-08). |
| RF-AUT-07 | Protección CSRF | Las peticiones mutantes requieren token CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`). Login y logout están exentos. Estado: implementado. |
| RF-AUT-08 | Usuario inactivo | Un usuario desactivado no puede iniciar sesión, aunque su contraseña sea correcta. Estado: implementado. |
| RF-AUT-09 | Usuarios iniciales | Si la tabla de usuarios está vacía, se crean las cuentas definidas en variables de entorno `SEED_ADMIN_*` y `SEED_CASHIER_*`. No hay credenciales por defecto en el código. Estado: implementado. |

### 4.2 Punto de venta (venta en mostrador)

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-VEN-01 | Catálogo de venta | Muestra solo productos **activos**. Permite buscar por nombre o descripción y filtrar por categoría activa. Estado: implementado. |
| RF-VEN-02 | Carrito | Permite agregar, quitar y cambiar cantidades. La cantidad máxima por producto está limitada por el stock disponible en pantalla. Estado: implementado. |
| RF-VEN-03 | Métodos de pago | Efectivo, débito, crédito y transferencia. **Una sola forma de pago por venta**. Estado: implementado. |
| RF-VEN-04 | Descuento | Descuento global en monto fijo (CLP). No puede superar el subtotal. No hay descuento porcentual ni por línea. Estado: implementado. |
| RF-VEN-05 | Pago en efectivo | Exige un monto recibido mayor o igual al total y calcula el vuelto. Estado: implementado. |
| RF-VEN-06 | Pago no efectivo | El monto recibido se iguala automáticamente al total; el vuelto es cero. Estado: implementado. |
| RF-VEN-07 | Caja obligatoria | No se puede registrar una venta si no hay una caja abierta. Estado: implementado. |
| RF-VEN-08 | Registro atómico | La venta, sus ítems, el descuento de stock y el movimiento de inventario se guardan en una sola transacción. Estado: implementado. |
| RF-VEN-09 | Control de sobreventa | Antes de vender se bloquea el producto y se valida el stock real; si no alcanza, la venta se rechaza. Estado: implementado. |
| RF-VEN-10 | Producto inactivo | Un producto desactivado no puede venderse aunque se intente por API. Estado: implementado. |
| RF-VEN-11 | Folio | Cada venta recibe un número único con formato `V-000001`, generado a partir del identificador. Estado: implementado. |
| RF-VEN-12 | Historial del día | Permite consultar las ventas de una fecha (por defecto hoy) con número, hora, cajero, pago, total y estado. La API incluye el detalle de ítems de cada venta. Estado: implementado. |
| RF-VEN-13 | Anulación | Solo un administrador puede anular una venta completa. La venta pasa a `VOIDED`, se restaura el stock y se registra el movimiento. Estado: implementado. |
| RF-VEN-14 | Desglose de IVA | La boleta y la vista previa muestran neto e IVA (19 %) calculados sobre el total. Es informativo; no genera documento tributario. Estado: implementado. |
| RF-VEN-15 | Precio histórico | Cada ítem guarda el nombre y el precio unitario al momento de la venta; cambios posteriores de precio no alteran ventas pasadas. Estado: implementado. |

### 4.3 Boleta / comprobante

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-BOL-01 | Generación | Genera un PDF de 80 mm en el navegador con jsPDF. Estado: implementado. |
| RF-BOL-02 | Contenido | Nombre del negocio, folio, fecha/hora, cajero, productos, cantidades, subtotal, descuento, neto, IVA, total, método de pago, recibido y vuelto. Estado: implementado. |
| RF-BOL-03 | Leyenda legal | Incluye el texto **“Sin valor tributario”**. No es una boleta electrónica del SII. Estado: implementado. |
| RF-BOL-04 | Vista previa | Se abre en una pestaña nueva del navegador. Estado: implementado. |
| RF-BOL-05 | Venta sin boleta | El operador puede registrar la venta sin generar el PDF. Estado: implementado. |
| RF-BOL-06 | Reimpresión inmediata | Mientras la venta recién registrada sigue en pantalla, se puede volver a abrir su boleta. Estado: implementado. |
| RF-BOL-07 | Almacenamiento | El PDF **no se guarda** en el servidor ni se asocia como archivo a la venta. Estado: implementado (solo en memoria del navegador). |

### 4.4 Control de caja

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-CAJ-01 | Apertura | Registra el monto inicial del turno. El monto no puede ser negativo. Estado: implementado. |
| RF-CAJ-02 | Una caja abierta | Solo puede existir **una sesión de caja abierta en todo el sistema** a la vez. Estado: implementado. |
| RF-CAJ-03 | Cierre | Solicita el efectivo contado y observaciones opcionales; cierra la sesión. Estado: implementado. |
| RF-CAJ-04 | Efectivo esperado | Se calcula como monto inicial + ventas **en efectivo** completadas del turno. Estado: implementado. |
| RF-CAJ-05 | Diferencia | Se calcula como efectivo contado − efectivo esperado; puede ser positiva, negativa o cero. Estado: implementado. |
| RF-CAJ-06 | Historial | Muestra las últimas **20** sesiones con apertura, cierre, responsable, montos, diferencia y estado. Estado: implementado. |
| RF-CAJ-07 | Trazabilidad | Registra quién abrió y quién cerró la caja. Estado: implementado. |
| RF-CAJ-08 | Bloqueo de ventas | Sin caja abierta, el POS bloquea el cobro y muestra un aviso. Estado: implementado. |

### 4.5 Productos y categorías

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-PRO-01 | Listado | Lista productos activos e inactivos en la administración; el POS solo muestra activos. Estado: implementado. |
| RF-PRO-02 | Creación | Nombre, descripción, precio, stock, stock mínimo y categoría. Estado: implementado. |
| RF-PRO-03 | Validaciones | Nombre obligatorio (máx. 120), descripción (máx. 300), precio mayor a cero, stock y stock mínimo no negativos, categoría existente. Estado: implementado. |
| RF-PRO-04 | Nombre único | No se permiten dos productos con el mismo nombre (sin distinguir mayúsculas). Estado: implementado. |
| RF-PRO-05 | Edición | Permite cambiar nombre, descripción, precio, stock, stock mínimo y categoría. Estado: implementado. |
| RF-PRO-06 | Ajuste de stock | Si la edición cambia el stock, se registra un movimiento `ADJUSTMENT` con usuario y motivo. Estado: implementado. |
| RF-PRO-07 | Reposición | Suma una cantidad positiva al stock y registra un movimiento `RESTOCK` con motivo y usuario. Estado: implementado. |
| RF-PRO-08 | Desactivación | Un producto se desactiva lógicamente; no se elimina de la base ni del historial. Estado: implementado. |
| RF-PRO-09 | Categorías | Listado, creación, edición y desactivación por API. En la interfaz solo se ofrece **crear** categorías. Estado: parcial (ver L-05). |
| RF-PRO-10 | Nombre de categoría único | No se permiten categorías duplicadas (sin distinguir mayúsculas). Estado: implementado. |
| RF-PRO-11 | Color de categoría | Cada categoría tiene un color hexadecimal usado como etiqueta visual. Estado: implementado. |

### 4.6 Inventario

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-INV-01 | Movimientos automáticos | Cada venta genera movimientos `SALE`; cada anulación genera `SALE_VOID`. Estado: implementado. |
| RF-INV-02 | Movimientos manuales | Los ajustes de stock y las reposiciones generan `ADJUSTMENT` y `RESTOCK`. Estado: implementado. |
| RF-INV-03 | Detalle del movimiento | Guarda producto, tipo, cantidad, stock anterior, stock nuevo, motivo, usuario y fecha. Estado: implementado. |
| RF-INV-04 | Consulta | Muestra los últimos **50** movimientos, solo para administradores. Estado: implementado. |
| RF-INV-05 | Alerta visual | En el catálogo se muestra “Sin stock”, “Stock bajo” o “Disponible” según el stock mínimo. Estado: implementado. |

### 4.7 Reportes

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-REP-01 | Rango de fechas | Genera un reporte entre dos fechas inclusivas; por defecto, hoy. Estado: implementado. |
| RF-REP-02 | Zona horaria | Los cortes diarios usan la zona `America/Santiago`. Estado: implementado. |
| RF-REP-03 | Solo ventas vigentes | Suma únicamente ventas `COMPLETED`; las anuladas quedan fuera. Estado: implementado. |
| RF-REP-04 | Indicadores | Total vendido, cantidad de ventas, unidades vendidas, efectivo, tarjetas (débito + crédito) y transferencias. Estado: implementado. |
| RF-REP-05 | Medios de pago | Desglose por método con cantidad de ventas y monto. Estado: implementado. |
| RF-REP-06 | Más vendidos | Top 10 de productos por unidades, con ingreso asociado. Estado: implementado. |
| RF-REP-07 | Detalle en pantalla | Tabla de ventas de la fecha “hasta”, con opción de anular (solo admin). Estado: implementado (ver L-06). |
| RF-REP-08 | Exportación Excel | Genera un `.xlsx` con tres hojas: **Resumen**, **Productos más vendidos** y **Detalle de ventas**. Estado: implementado. |
| RF-REP-09 | Descarga automática | Al presionar “Generar reporte (Excel)” se carga el resumen y se descarga el archivo. Estado: implementado. |
| RF-REP-10 | Nombre del archivo | `ventas-del-dia-DD-MM-AAAA.xlsx` o `reporte-ventas-DD-MM-AAAA_a_DD-MM-AAAA.xlsx`. Estado: implementado. |

### 4.8 Usuarios

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-USU-01 | Listado | Solo administradores; ordenado por nombre completo. Estado: implementado. |
| RF-USU-02 | Creación | Usuario, nombre completo, contraseña, rol y estado activo. Estado: implementado. |
| RF-USU-03 | Edición | Permite cambiar usuario, nombre, rol, estado y, opcionalmente, la contraseña. Estado: implementado. |
| RF-USU-04 | Nombre de usuario único | No se permiten duplicados (sin distinguir mayúsculas). Estado: implementado. |
| RF-USU-05 | Política de contraseña | Mínimo 8 caracteres, al menos una mayúscula y al menos un número. Estado: implementado. |
| RF-USU-06 | Último administrador | No se puede desactivar ni degradar al último administrador activo. Estado: implementado. |
| RF-USU-07 | Baja lógica | Un usuario no se elimina; se desactiva. Estado: implementado. |
| RF-USU-08 | Cambio de contraseña | Un administrador puede cambiarla desde la pantalla de Usuarios. No existe autoservicio para el cajero. Estado: parcial (ver L-10). |

### 4.9 Operación y despliegue

| ID | Requerimiento | Comportamiento |
|---|---|---|
| RF-OPE-01 | Aplicación web única | El backend sirve la API y los archivos estáticos del frontend en el mismo origen. Estado: implementado. |
| RF-OPE-02 | Configuración por entorno | Conexión a PostgreSQL y usuarios iniciales se definen por variables de entorno; las credenciales no se versionan. Estado: implementado. |
| RF-OPE-03 | Contenedor | Dockerfile multi-etapa: Node para el frontend, Maven para el backend y JRE 17 para ejecutar; corre con usuario no root. Estado: implementado. |
| RF-OPE-04 | Despliegue | `deploy.sh` actualiza el código, reconstruye la imagen, reinicia el contenedor y verifica HTTP en el puerto configurado. Estado: implementado. |
| RF-OPE-05 | Persistencia | PostgreSQL es externo al contenedor; la aplicación no incluye un motor de base de datos. Estado: implementado. |
| RF-OPE-06 | Esquema | Hibernate crea/actualiza las tablas automáticamente (`ddl-auto=update`). Estado: implementado (ver L-13). |
| RF-OPE-07 | Datos iniciales | Si no hay productos, se cargan categorías y productos de ejemplo de sushi. Estado: implementado. |
| RF-OPE-08 | Verificación | El script de despliegue consulta la raíz de la aplicación y falla si no responde. Estado: implementado. |

---

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-01 | No se puede vender sin una caja abierta. |
| RN-02 | Solo puede existir una caja abierta a la vez en todo el sistema. |
| RN-03 | Una venta debe tener al menos un ítem y cada cantidad debe ser mayor a cero. |
| RN-04 | No se puede vender más unidades que el stock disponible. |
| RN-05 | No se puede vender un producto inactivo. |
| RN-06 | El descuento no puede ser negativo ni superar el subtotal. |
| RN-07 | En efectivo, el monto recibido debe cubrir el total; el vuelto se calcula automáticamente. |
| RN-08 | En pagos no efectivos, el monto recibido se iguala al total y el vuelto es cero. |
| RN-09 | Toda venta descuenta stock y genera un movimiento de inventario en la misma transacción. |
| RN-10 | Anular una venta restaura el stock y genera el movimiento `SALE_VOID`. |
| RN-11 | Una venta anulada no suma en reportes ni en el efectivo esperado de la caja. |
| RN-12 | El efectivo esperado de caja solo considera ventas en efectivo completadas. |
| RN-13 | La diferencia de caja es informativa; el sistema no impide cerrar con diferencia. |
| RN-14 | El precio y el nombre del producto se congelan en el ítem de venta. |
| RN-15 | El folio de venta es único y no se reutiliza. |
| RN-16 | Los nombres de productos, categorías y usuarios son únicos sin distinguir mayúsculas. |
| RN-17 | El stock mínimo es solo un umbral visual; no bloquea ventas ni genera notificaciones. |
| RN-18 | El IVA (19 %) es informativo y se calcula como `total / 1,19`; los precios se tratan como valores finales con IVA incluido. |
| RN-19 | La moneda es CLP y la zona horaria es `America/Santiago`; no son configurables desde la interfaz. |
| RN-20 | No se puede desactivar ni cambiar de rol al último administrador activo. |
| RN-21 | Un usuario inactivo no puede autenticarse. |
| RN-22 | Las credenciales de base de datos y los usuarios iniciales se definen por entorno, nunca en el repositorio. |

---

## 6. Matriz de permisos

| Función | ADMIN | CASHIER |
|---|:---:|:---:|
| Iniciar / cerrar sesión | Sí | Sí |
| Ver punto de venta y registrar ventas | Sí | Sí |
| Consultar ventas del día | Sí | Sí |
| Anular ventas | **Sí** | No |
| Abrir / cerrar caja y ver historial | Sí | Sí |
| Ver catálogo y categorías | Sí | Sí |
| Crear / editar / reponer / desactivar productos | **Sí** | No |
| Crear / editar / desactivar categorías | **Sí** | No |
| Ver movimientos de inventario | **Sí** | No |
| Ver reportes y descargar Excel | Sí | Sí |
| Gestionar usuarios y roles | **Sí** | No |

> La matriz refleja las reglas del backend. La interfaz oculta algunas opciones,
> pero la API es la que impone el permiso.

---

## 7. Requerimientos no funcionales

### 7.1 Seguridad

| ID | Requerimiento |
|---|---|
| RNF-SEG-01 | Contraseñas almacenadas con BCrypt; nunca en texto plano. |
| RNF-SEG-02 | Sesión por cookie `HttpOnly` y `SameSite=Strict`; expira a las 2 horas. |
| RNF-SEG-03 | Protección CSRF en peticiones mutantes. |
| RNF-SEG-04 | Cabeceras de seguridad: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Content-Security-Policy`, `Referrer-Policy` y HSTS cuando la conexión es HTTPS. |
| RNF-SEG-05 | Límite de intentos de login por IP. |
| RNF-SEG-06 | CORS restringido a orígenes configurados; por defecto, el frontend local de desarrollo. |
| RNF-SEG-07 | Credenciales fuera del repositorio; se inyectan por variables de entorno. |
| RNF-SEG-08 | La aplicación no implementa HTTPS por sí misma; en producción debe publicarse detrás de un proxy inverso con TLS. |

### 7.2 Rendimiento y concurrencia

| ID | Requerimiento |
|---|---|
| RNF-REN-01 | Operación pensada para un local pequeño, con una sola instancia y bajo volumen de ventas. |
| RNF-REN-02 | Las ventas usan bloqueo pesimista por producto para evitar sobreventa entre cajas o pestañas simultáneas. |
| RNF-REN-03 | Los listados no tienen paginación; el historial de caja se limita a 20 sesiones y el de inventario a 50 movimientos. |
| RNF-REN-04 | Los reportes filtran por rango de fechas en la base de datos. |

### 7.3 Disponibilidad y operación

| ID | Requerimiento |
|---|---|
| RNF-DIS-01 | El contenedor se reinicia automáticamente salvo que se detenga manualmente. |
| RNF-DIS-02 | No hay alta disponibilidad, réplicas ni balanceo; una sola instancia atiende a todos los usuarios. |
| RNF-DIS-03 | El sistema requiere que PostgreSQL y el servidor estén disponibles; no opera sin conexión. |
| RNF-DIS-04 | Los respaldos de la base de datos son responsabilidad del operador; no hay respaldo automático. |
| RNF-DIS-05 | El despliegue es manual mediante script; no hay CI/CD ni ambiente de staging. |

### 7.4 Compatibilidad y usabilidad

| ID | Requerimiento |
|---|---|
| RNF-USA-01 | Interfaz web en español, orientada a escritorio y con adaptación a pantallas menores. |
| RNF-USA-02 | Requiere un navegador moderno con JavaScript, cookies y ventanas emergentes habilitadas para el PDF. |
| RNF-USA-03 | Montos en CLP sin decimales; fechas y horas en formato chileno. |
| RNF-USA-04 | Confirmaciones y alertas para acciones destructivas o irreversibles. |
| RNF-USA-05 | La boleta se imprime desde el visor PDF del navegador; no hay impresión directa a impresora térmica. |

### 7.5 Mantenibilidad y tecnología

| ID | Requerimiento |
|---|---|
| RNF-TEC-01 | Backend: Java 17, Spring Boot 4.1.2, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, Apache POI. |
| RNF-TEC-02 | Frontend: React 19, React Router 7, TypeScript 5.8, Vite 6, jsPDF, SweetAlert2. |
| RNF-TEC-03 | Build reproducible con Maven Wrapper y `npm ci`; imagen Docker multi-etapa. |
| RNF-TEC-04 | Pruebas automatizadas: solo una prueba de carga de contexto con H2; no hay pruebas unitarias de reglas de negocio ni pruebas E2E. |
| RNF-TEC-05 | No hay herramienta de migraciones versionadas (Flyway/Liquibase); el esquema lo administra Hibernate. |

---

## 8. Modelo de datos (resumen)

| Tabla | Propósito | Campos relevantes |
|---|---|---|
| `app_users` | Usuarios del sistema | usuario único, contraseña BCrypt, nombre, rol, activo, fecha de creación |
| `categories` | Categorías del catálogo | nombre único, color, activa |
| `products` | Productos vendibles | nombre único, descripción, precio, stock, stock mínimo, categoría, activo, fecha de actualización |
| `sales` | Cabecera de venta | folio único, cajero, sesión de caja, método de pago, estado, subtotal, descuento, total, recibido, vuelto, fecha |
| `sale_items` | Detalle de venta | venta, producto (puede quedar nulo), nombre congelado, precio unitario, cantidad, total de línea |
| `cash_sessions` | Turnos de caja | estado, usuario apertura/cierre, monto inicial, monto contado, esperado, diferencia, fechas, notas |
| `stock_movements` | Historial de inventario | producto, tipo, cantidad, stock anterior/nuevo, motivo, usuario, fecha |

Relaciones principales:

- Una venta pertenece a un cajero y a una sesión de caja.
- Una venta tiene uno o más ítems.
- Un producto pertenece a una categoría.
- Un movimiento de stock pertenece a un producto y opcionalmente a un usuario.
- Las ventas conservan el nombre y precio del producto aunque el producto se edite o desactive.

---

## 9. Interfaces del sistema

### 9.1 Pantallas

| Ruta | Pantalla | Acceso |
|---|---|---|
| `/login` | Inicio de sesión | Público |
| `/` | Punto de venta | Autenticado |
| `/caja` | Control de caja | Autenticado |
| `/productos` | Productos e inventario | Autenticado (acciones de administración solo `ADMIN`) |
| `/reportes` | Reportes de venta | Autenticado |
| `/usuarios` | Usuarios y roles | Solo `ADMIN` |

### 9.2 API REST

| Método | Ruta | Permiso | Uso |
|---|---|---|---|
| `POST` | `/api/auth/login` | Público | Iniciar sesión |
| `GET` | `/api/auth/me` | Autenticado | Usuario actual |
| `POST` | `/api/auth/logout` | Autenticado | Cerrar sesión |
| `GET` | `/api/auth/csrf-token` | Público | Obtener token CSRF |
| `GET` | `/api/products` | Autenticado | Listar productos |
| `GET` | `/api/products/{id}` | Autenticado | Ver producto |
| `POST` | `/api/products` | `ADMIN` | Crear producto |
| `PUT` | `/api/products/{id}` | `ADMIN` | Editar producto |
| `PATCH` | `/api/products/{id}/restock` | `ADMIN` | Reponer stock |
| `DELETE` | `/api/products/{id}` | `ADMIN` | Desactivar producto |
| `GET` | `/api/categories` | Autenticado | Listar categorías |
| `POST` | `/api/categories` | `ADMIN` | Crear categoría |
| `PUT` | `/api/categories/{id}` | `ADMIN` | Editar categoría |
| `DELETE` | `/api/categories/{id}` | `ADMIN` | Desactivar categoría |
| `GET` | `/api/inventory/movements` | `ADMIN` | Últimos movimientos de stock |
| `POST` | `/api/sales` | Autenticado | Registrar venta |
| `GET` | `/api/sales?date=AAAA-MM-DD` | Autenticado | Ventas de una fecha |
| `GET` | `/api/sales/{id}` | Autenticado | Detalle de venta |
| `POST` | `/api/sales/{id}/void` | `ADMIN` | Anular venta |
| `GET` | `/api/cash/current` | Autenticado | Caja abierta actual |
| `POST` | `/api/cash/open` | Autenticado | Abrir caja |
| `POST` | `/api/cash/close` | Autenticado | Cerrar caja |
| `GET` | `/api/cash/sessions` | Autenticado | Historial de cajas |
| `GET` | `/api/reports?from=&to=` | Autenticado | Resumen de ventas |
| `GET` | `/api/reports/excel?from=&to=` | Autenticado | Exportar Excel |
| `GET` | `/api/users` | `ADMIN` | Listar usuarios |
| `POST` | `/api/users` | `ADMIN` | Crear usuario |
| `PUT` | `/api/users/{id}` | `ADMIN` | Editar usuario |

### 9.3 Integraciones externas

| Integración | Uso |
|---|---|
| PostgreSQL | Persistencia de todos los datos. |
| Navegador (jsPDF) | Generación y visualización de la boleta PDF. |
| Apache POI | Generación del archivo Excel en el servidor. |

No hay integración con pasarelas de pago, SII, impresoras, balanzas, lectores de
código de barras, correo electrónico ni servicios de mensajería.

---

## 10. Lo que el sistema **sí** hace (resumen ejecutivo)

1. Permite vender productos de un catálogo con control de stock.
2. Exige abrir caja antes de vender y calcula el efectivo esperado al cerrar.
3. Registra ventas con folio, método de pago, descuento, vuelto y desglose de IVA.
4. Descuenta stock automáticamente y deja historial de cada movimiento.
5. Permite anular ventas completas (solo administrador) y restaura el stock.
6. Administra productos, categorías, precios, stock y stock mínimo.
7. Genera reportes por rango de fechas y los exporta a Excel.
8. Administra usuarios con dos roles y protección del último administrador.
9. Genera una boleta PDF de 80 mm como comprobante interno.
10. Se despliega en un servidor propio con Docker y PostgreSQL externo.

---

## 11. Lo que el sistema **no** hace (exclusiones explícitas)

Esta sección es tan importante como la anterior: describe funciones que **no
están implementadas** y que no deben suponerse al operar el negocio.

### 11.1 Operación comercial

| ID | No soportado |
|---|---|
| NS-01 | Mesas, salón, comandas, unión/separación de cuentas y traspaso de mesas. |
| NS-02 | Delivery, retiro programado, reparto ni integración con plataformas de pedidos. |
| NS-03 | Propinas, cargos por servicio o recargos. |
| NS-04 | Múltiples sucursales, bodegas o cajas independientes por terminal. |
| NS-05 | Varios turnos de caja simultáneos o una caja por cajero. |
| NS-06 | Reservas, lista de espera, fidelización, cupones o tarjetas de regalo. |
| NS-07 | Gestión de clientes, historial de compras por cliente o crédito. |
| NS-08 | Pedidos pendientes, preventa, apartados o cotizaciones. |
| NS-09 | Combos armados dinámicamente, modificadores, extras o notas por producto. |
| NS-10 | Balanzas, códigos de barras, búsqueda por código o SKU. |

### 11.2 Pagos y documentos tributarios

| ID | No soportado |
|---|---|
| NS-11 | Pago mixto o dividido entre varios métodos. |
| NS-12 | Pago con tarjeta integrado; solo se registra el método elegido. |
| NS-13 | Devoluciones parciales, cambios de producto o notas de crédito. |
| NS-14 | Reembolso automático del pago al anular una venta. |
| NS-15 | Facturación electrónica, boleta electrónica del SII, DTE o libros tributarios. |
| NS-16 | Configuración de impuestos, retenciones o exenciones. |
| NS-17 | Envío de boleta por correo, WhatsApp o impresión directa en térmica. |
| NS-18 | Numeración tributaria o timbre electrónico. |

### 11.3 Inventario y costos

| ID | No soportado |
|---|---|
| NS-19 | Ingredientes, recetas, consumo por porción o descuento de insumos. |
| NS-20 | Costos, márgenes, utilidad, food cost o valorización de inventario. |
| NS-21 | Compras a proveedores, órdenes de compra, recepciones o cuentas por pagar. |
| NS-22 | Transferencias entre bodegas o sucursales. |
| NS-23 | Inventario físico con conteo, mermas o ajustes por auditoría. |
| NS-24 | Alertas automáticas de stock bajo por correo o notificación. |
| NS-25 | Reactivación de un producto desactivado desde la interfaz. |
| NS-26 | Historial de precios o programación de cambios de precio. |
| NS-27 | Imágenes, fichas técnicas o variantes de producto. |

### 11.4 Caja y finanzas

| ID | No soportado |
|---|---|
| NS-28 | Ingresos o retiros de efectivo durante el turno (solo apertura y cierre). |
| NS-29 | Conteo por denominaciones de billetes y monedas. |
| NS-30 | Apertura o cierre programado por horario. |
| NS-31 | Reapertura o corrección de una caja ya cerrada. |
| NS-32 | Ajuste automático de una caja cerrada al anular ventas posteriores. |
| NS-33 | Cuadratura obligatoria: se puede cerrar con diferencia sin justificación. |
| NS-34 | Exportación de cajas o inventario a Excel/CSV (solo ventas). |
| NS-35 | Flujo de caja, cuentas por cobrar/pagar, contabilidad o conciliación bancaria. |

### 11.5 Reportes y análisis

| ID | No soportado |
|---|---|
| NS-36 | Reportes por cajero, turno, categoría o producto individual. |
| NS-37 | Comparación entre períodos, gráficos históricos o proyecciones. |
| NS-38 | Reporte de ventas anuladas, motivos de anulación o auditoría de cambios. |
| NS-39 | Reporte de mermas, compras, costos o rentabilidad. |
| NS-40 | Reporte consolidado de varias sucursales. |
| NS-41 | Exportación de reportes a PDF (solo Excel). |
| NS-42 | Envío programado de reportes por correo. |

### 11.6 Usuarios y seguridad

| ID | No soportado |
|---|---|
| NS-43 | Roles adicionales o permisos granulares (solo `ADMIN` y `CASHIER`). |
| NS-44 | Autoservicio de cambio o recuperación de contraseña. |
| NS-45 | Autenticación de dos factores (2FA), SSO o inicio con Google/Microsoft. |
| NS-46 | Bloqueo por usuario; el límite de intentos es por IP. |
| NS-47 | Historial de inicios de sesión o de acciones administrativas. |
| NS-48 | Eliminación definitiva de usuarios; solo desactivación. |
| NS-49 | Firma electrónica o aprobación de supervisor para anulaciones. |
| NS-50 | API pública documentada (OpenAPI/Swagger) o claves de API para terceros. |

### 11.7 Técnicas y de infraestructura

| ID | No soportado |
|---|---|
| NS-51 | Funcionamiento offline o sincronización posterior. |
| NS-52 | Aplicación móvil nativa o de escritorio. |
| NS-53 | Multi-idioma (solo español) y multi-moneda (solo CLP). |
| NS-54 | Configuración de zona horaria desde la interfaz (fija en `America/Santiago`). |
| NS-55 | HTTPS integrado; requiere proxy inverso con certificado. |
| NS-56 | Alta disponibilidad, réplicas, clúster o balanceo de carga. |
| NS-57 | Migraciones de base de datos versionadas y reversibles. |
| NS-58 | Respaldos automáticos o restauración desde la aplicación. |
| NS-59 | Monitoreo, métricas, alertas o trazas distribuidas. |
| NS-60 | CI/CD, ambientes de staging o despliegue canario. |
| NS-61 | Pruebas automatizadas de reglas de negocio, integración o E2E. |
| NS-62 | Paginación o búsqueda avanzada en listados grandes. |

---

## 12. Limitaciones conocidas y riesgos

| ID | Limitación | Impacto / recomendación |
|---|---|---|
| L-01 | Solo existe **una caja abierta global**. | Si hay dos terminales, la segunda no puede abrir caja hasta que la primera cierre. Para varios puestos se necesita una caja por terminal o una sesión por usuario. |
| L-02 | El historial de caja muestra solo las últimas 20 sesiones y el de inventario los últimos 50 movimientos. | La información antigua no desaparece, pero no es consultable desde la interfaz. |
| L-03 | No hay paginación en ventas, productos ni usuarios. | Con muchos registros la interfaz puede volverse lenta. |
| L-04 | La tabla de detalle de Reportes usa solo la fecha **“hasta”**, aunque el resumen sí cubre el rango completo. | Puede confundir al comparar el resumen con el detalle. Revisar antes de tomar decisiones. |
| L-05 | La interfaz solo permite **crear** categorías; editar y desactivar existe por API pero no está expuesto en pantalla. | Un error de nombre o color no se corrige desde la interfaz. |
| L-06 | Un producto desactivado **no se puede reactivar** desde la interfaz ni por la API actual. | La desactivación es irreversible sin intervención directa en la base de datos. Confirmar antes de desactivar. |
| L-07 | La boleta se genera en el navegador y **no se almacena**. No hay forma de reimprimir una boleta antigua desde Reportes. | Si se cierra la pestaña o se pierde el PDF, no se puede recuperar desde el sistema. |
| L-08 | El límite de login es por IP y en memoria. Además, el filtro cuenta **todo** `POST /api/auth/login` y suma otro intento cuando las credenciales fallan. | En una misma IP pueden bloquearse inicios de sesión antes de los 5 fallos si hubo accesos exitosos recientes. El contador se reinicia al reiniciar la aplicación y no se comparte entre instancias. |
| L-09 | El IVA es referencial y está fijo en 19 %. | No sirve para declaraciones tributarias. La boleta dice “Sin valor tributario”. |
| L-10 | No hay autoservicio de contraseña para el cajero; debe hacerlo un administrador. | Dependencia operativa del administrador. |
| L-11 | El nombre del negocio está fijo como “Sushi Local” en la interfaz y en la boleta. | La variable `BUSINESS_NAME` existe en el backend, pero no se refleja en el frontend ni en el PDF. |
| L-12 | Los montos se ingresan como enteros en CLP; la interfaz elimina decimales. | No se pueden cobrar fracciones de peso, aunque la base tenga dos decimales. |
| L-13 | El esquema se actualiza con `ddl-auto=update`, sin migraciones versionadas. | Cambios de modelo pueden ser difíciles de auditar o revertir. Se recomienda respaldar antes de actualizar. |
| L-14 | Solo hay una prueba automatizada de carga de contexto. | Las reglas de negocio no tienen cobertura automatizada; los cambios requieren pruebas manuales. |
| L-15 | No hay respaldo automático de PostgreSQL. | Una falla del servidor o un error humano puede provocar pérdida de datos. Configurar respaldos y probar la restauración. |
| L-16 | La aplicación no publica HTTPS por sí misma y la cookie `Secure` viene desactivada por defecto. | En producción se debe usar un proxy inverso con TLS y activar `SESSION_COOKIE_SECURE=true`. |
| L-17 | Anular una venta después de cerrar la caja restaura el stock, pero **no recalcula** el efectivo esperado ni la diferencia de la sesión ya cerrada. | El arqueo histórico queda como se cerró. Documentar y revisar anulaciones posteriores al cierre. |
| L-18 | No hay auditoría de cambios de usuarios, precios o cajas; solo se registran movimientos de stock y estados de venta. | Para investigar incidentes falta trazabilidad administrativa. |
| L-19 | Si no se definen usuarios iniciales por entorno en el primer arranque, no hay usuario para entrar y crear el primero desde la interfaz. | Configurar `SEED_ADMIN_*` antes del primer inicio o crear el usuario directamente en la base. |
| L-20 | El sistema asume una sola instancia. | Escalar horizontalmente rompería el límite de login en memoria y complicaría la caja global. |

---

## 13. Requisitos mínimos para operar

Antes de usar el sistema en el local, el operador debe garantizar:

1. **Servidor** con Docker y Docker Compose, o ejecución manual de backend y frontend.
2. **PostgreSQL** accesible con una base de datos creada y un usuario con permisos.
3. Variables de entorno definidas: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
4. Usuarios iniciales definidos en el primer arranque: `SEED_ADMIN_USERNAME`,
   `SEED_ADMIN_PASSWORD` y, opcionalmente, `SEED_CASHIER_*`.
5. **Puerto** de aplicación definido (`APP_PORT`, por defecto 8000 en Docker).
6. **Respaldo** de la base de datos configurado y probado.
7. **HTTPS** mediante proxy inverso si el sistema se expone fuera de la red local.
8. **Navegador** moderno con ventanas emergentes permitidas para la boleta.
9. **Impresora** configurada en el sistema operativo para imprimir el PDF de 80 mm.
10. **Responsable** definido para abrir/cerrar caja y administrar usuarios.

---

## 14. Criterios de aceptación (checklist de operación)

### 14.1 Flujo principal

- [ ] Un administrador puede iniciar sesión con las credenciales definidas por entorno.
- [ ] Se puede abrir caja con un monto inicial.
- [ ] Se puede agregar un producto al carrito y cobrar en efectivo.
- [ ] El sistema calcula vuelto y muestra el desglose de IVA.
- [ ] El stock del producto disminuye y aparece el movimiento `SALE`.
- [ ] Se genera y visualiza la boleta PDF.
- [ ] Se puede cerrar caja con el efectivo contado y ver la diferencia.
- [ ] El reporte del día muestra la venta y el Excel se descarga con las tres hojas.
- [ ] Un administrador puede anular la venta y el stock se restaura.

### 14.2 Validaciones negativas

- [ ] No se puede vender sin caja abierta.
- [ ] No se puede vender más que el stock disponible.
- [ ] No se puede aplicar un descuento mayor al subtotal.
- [ ] En efectivo, no se puede cobrar con un monto menor al total.
- [ ] Un cajero no puede anular ventas, gestionar usuarios ni ver movimientos de inventario.
- [ ] No se puede desactivar ni degradar al último administrador activo.
- [ ] Un usuario inactivo no puede iniciar sesión.

### 14.3 Operación

- [ ] El contenedor responde HTTP en el puerto configurado.
- [ ] La base de datos conserva los datos tras reiniciar el contenedor.
- [ ] Existe un respaldo reciente y se probó su restauración.
- [ ] Las credenciales no están en el repositorio ni en archivos versionados.

---

## 15. Glosario

| Término | Definición |
|---|---|
| **ADMIN** | Rol con acceso total, incluida la anulación de ventas y la gestión de usuarios. |
| **CASHIER** | Rol operativo de venta y caja. |
| **Caja cerrada** | Sesión de caja finalizada; no permite nuevas ventas hasta abrir otra. |
| **Diferencia de caja** | Efectivo contado menos efectivo esperado. |
| **Efectivo esperado** | Monto inicial más ventas en efectivo completadas del turno. |
| **Folio** | Identificador correlativo interno de la venta. |
| **Movimiento de stock** | Registro de un cambio de inventario por venta, anulación, reposición o ajuste. |
| **POS** | Punto de venta. |
| **Soft delete** | Baja lógica: el registro se marca inactivo, no se elimina físicamente. |
| **Venta anulada** | Venta revertida que no suma en reportes y restaura stock. |

---

## 16. Control de cambios

| Versión | Fecha | Descripción |
|---|---|---|
| 1.0 | 2026-11-15 | Documento inicial de requerimientos, basado en la revisión del código fuente. |
