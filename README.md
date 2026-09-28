## Diagnóstico inicial (antes de refactorizar)

Se analizó `GestorPedidos.java` . Su único método público,
`procesarPedido()`.

### 1. Razones para cambiar: 6 responsabilidades en un solo método

| Líneas | Responsabilidad | Evidencia |
|--------|-----------------|-----------|
| 29-44  | Validación de stock | `SELECT stock FROM inventario` dentro de un `for`, con `return rechazado(...)` |
| 47-65  | Validación de cliente y mora | `SELECT tipo_cliente`, `SELECT SUM(monto) FROM facturas` y excepción por `LocalTime.now()` |
| 68-73  | Cálculo de subtotal | `SELECT precio FROM productos` ejecutado una vez por ítem |
| 76-93  | Descuento, impuesto y total | `if (tipoCliente.equals("VIP"))` con `if/else if` internos; `* 0.19` fijo |
| 99-113 | Persistencia | `INSERT INTO pedidos`, `INSERT INTO detalle_pedido`, `UPDATE inventario`, sin repositorio ni transacción |
| 116-133 | Notificación | `StringBuilder cuerpo` armando el texto del correo + `emailService.enviar` |

**Validacion de stock :** Consulta la base de datos directamente e interrumpe el flujo si no hay stock suficiente.   
**Validación de cliente y mora :** Realiza consultas SQL para verificar la existencia del cliente, calcula deudas pendientes de facturas y aplica reglas condicionales basadas en la hora del sistema (`LocalTime.now()`).   
**Cálculo de subtotal :**  Realiza lecturas directas a la tabla `productos` mediante consultas SQL iterativas por cada ítem.  
**Descuento, impuesto y total :** Aplica la lógica de negocio para determinar porcentajes de descuento según tipo de cliente e historial, e calcula impuestos y totales.    
**Persistencia :**Realiza inserciones en las tablas `pedidos` y `detalle_pedido`, y actualiza manualmente la tabla `inventario` con consultas SQL explícitas sin abstracción de repositorio ni manejo transaccional.   
**Notificación :** Formatea el correo electrónico mediante un `StringBuilder` y gestiona el envío de notificaciones por email.   

Un cambio en las tarifas de descuento, en el formato del correo, en el esquema
de la BD o en la política de mora obliga a editar la misma clase.

### 2. God Object (nivel de clase)

- `GestorPedidos` depende directamente de `JdbcTemplate` y de `EmailService`
  (campos `@Autowired`, líneas 19-23) y ejecuta SQL de 4 tablas distintas
  (`inventario`, `clientes`/`facturas`, `productos`, `pedidos`/`detalle_pedido`).
- Contiene lógica de validación, de negocio, de acceso a datos y de presentación
  (texto del correo) en una sola clase.
- Los 6 métodos privados auxiliares (`obtenerHistorialCliente`,
  `formatearFactura`, `calcularImpuestoRegional`, `reintentarNotificacion`,
  `purgarPedidosVencidos`, `construirCuerpoCorreo`) confirman que la clase
  acumula funciones ajenas entre sí, hasta llegar a 340 líneas.
- No se puede probar una regla de negocio (p. ej. el descuento VIP) sin base de
  datos, porque la regla y el SQL viven en el mismo método.

### 3. Spaghetti Code (nivel de método)

- **Anidamiento:** el bloque de mora llega a **3 niveles** (líneas 50-61:
  `else if MOROSO` → `if deuda != null && deuda > 0` → `if ahora.isBefore(20:00)`
  / `else`). El bloque de descuento llega a **2 niveles** (líneas 75-91:
  `if VIP` → `if subtotal > 1_000_000 / > 500_000 / else`).
- **Mezcla de niveles de abstracción:** en la misma secuencia de líneas
  conviven SQL embebido (líneas 35, 45, 68, 97), reglas de negocio (descuentos),
  lectura del reloj del sistema (`LocalTime.now()`, línea 55), formato de texto
  (líneas 114-121) y manejo de errores (`try/catch`, líneas 123-128).
- **Estado compartido entre bloques:** `tipoCliente` se lee en la validación
  (línea 45) pero se usa 30 líneas después en el descuento (línea 75);
  `subtotal` viaja igual. Los bloques no se pueden entender ni mover por separado.
- **Flujo de control disperso:** 5 `return` tempranos de rechazo repartidos a
  lo largo del método (líneas 32, 40, 49, 58, más el flujo normal en 131).
- **Consultas repetidas:** una consulta SQL por ítem en el stock (línea 35) y otra
  por ítem en el precio (línea 68), sin ninguna abstracción.

### 4. Costo de extender: nuevo tipo de cliente

Agregar un tipo de cliente con reglas de descuento propias obliga a modificar
el bloque de las líneas 75-91 (nueva rama `else if`), dentro de un método de
~107 líneas cuyas variables (`tipoCliente`, `subtotal`, `descuento`) se comparten
con la validación, la persistencia y el correo. Si además ese tipo tiene una
regla de validación propia (como MOROSO), hay que tocar también las líneas 44-63.
Es decir, se viola el principio Abierto/Cerrado: agregar un caso exige
modificar código existente y releer el método completo para no romperlo.

## Decisiones de Diseño (Parte 1) 
### Validación: Chain of Responsibility   
- **Elección:** Se eligió *Chain of Responsibility* para encadenar las validaciones porque existe una dependencia de orden estricta y necesidad de corte anticipado (ej. si el stock falla, no se debe consultar el estado de mora del cliente).    
- **Alternativa descartada:** Una lista de métodos booleanos o `Predicate<ContextoPedido>`. Se descartó porque evalúa todos los predicados sin permitir un corte limpio y encapsulado de la cadena.   

### Descuentos: Strategy
- **Elección:** Se eligió *Strategy* para encapsular los algoritmos de cálculo de descuento por tipo de cliente, ya que son mutuamente excluyentes y no dependen de un orden de ejecución.    
- **Alternativa descartada:** Incluir el cálculo dentro de la cadena de validación. Se descartó porque los descuentos no validan ni rechazan pedidos; mezclarlos en la cadena violaría el principio de responsabilidad única.   

