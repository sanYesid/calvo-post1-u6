# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño
de Software — Sexto Semestre. Un único proyecto Spring Boot
(pedidos-service/) con dos partes: diagnóstico y refactorización de
un antipatrón combinado en GestorPedidos, y diagnóstico y corrección
de un segundo antipatrón introducido al hacer crecer el mismo
proyecto con tres campañas de descuento.



## Decisiones de diseño

### Parte 1 — GestorPedidos

### Diagnóstico inicial (antes de refactorizar)

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
**Persistencia :** Realiza inserciones en las tablas `pedidos` y `detalle_pedido`, y actualiza manualmente la tabla `inventario` con consultas SQL explícitas sin abstracción de repositorio ni manejo transaccional.   
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

## Patrones de diseño aplicados  
### Validación: Chain of Responsibility   
- **Elección:** Se eligió *Chain of Responsibility* para encadenar las validaciones porque existe una dependencia de orden estricta y necesidad de corte anticipado (ej. si el stock falla, no se debe consultar el estado de mora del cliente).    
- **Alternativa descartada:** Una lista de métodos booleanos o `Predicate<ContextoPedido>`. Se descartó porque evalúa todos los predicados sin permitir un corte limpio y encapsulado de la cadena.   

### Descuentos: Strategy
- **Elección:** Se eligió *Strategy* para encapsular los algoritmos de cálculo de descuento por tipo de cliente, ya que son mutuamente excluyentes y no dependen de un orden de ejecución.    
- **Alternativa descartada:** Incluir el cálculo dentro de la cadena de validación. Se descartó porque los descuentos no validan ni rechazan pedidos; mezclarlos en la cadena violaría el principio de responsabilidad única.   


### Parte 2 — Crecimiento del proyecto

### Diagnóstico de la Parte 2 (antes de corregir): Golden Hammer

**Contexto.** Mercadeo pidió tres campañas: BLACK_FRIDAY (25%), CORPORATIVO (10%)
y VOLUMEN (12%). Se implementaron como tres eslabones nuevos de la cadena de
validación (`PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen`),
que pasó a tener 5 eslabones: 2 de validación real y 3 de "promoción". El código
compila y calcula bien; el problema no es funcional sino de diseño.

### 1. Test de forma: ¿el problema tenía forma de cadena?

En la Parte 1 se eligió Chain of Responsibility por tres propiedades. Se
verificó si las clases nuevas las cumplen:

| Propiedad que justifica la cadena | ValidadorStock / ValidadorCliente | PromocionBlackFriday / Corporativo / Volumen |
|---|---|---|
| Dependencia de orden real | Sí: si el stock falla no se consulta al cliente, y el motivo de rechazo depende de quién corre primero | No: todas escriben con `aplicarDescuentoCampana`, que conserva el mayor valor |
| Corte anticipado | Sí: llaman `contexto.rechazar(...)` | No: ninguna llama a `rechazar` |
| Contrato "decidir si el pedido continúa o se rechaza" | Sí | No: solo calculan un porcentaje |

Las tres clases nuevas incumplen las tres propiedades, es decir, el problema no
tenía forma de cadena.

### 2. ¿Hay dependencia de orden entre las campañas, o con los validadores?

**Verificación de equivalencia:** como `PromocionBlackFriday`, `PromocionCorporativo`
y `PromocionVolumen` se eliminaron del proyecto (Paso 7), no es posible reejecutar
la versión con los tres eslabones para comparar salidas directamente. En su lugar,
se calculó a mano el resultado esperado con la fórmula original
(`Math.max(descuentoTipoCliente, descuentoCampana)`, donde `descuentoCampana` es
el mayor de las tres campañas) y se comparó contra el total que produce
`CalculadorDescuentoFinal`, verificado con `GestorPedidosTest`:

| Pedido de prueba | Campaña relevante | Cálculo manual esperado | Total obtenido (test) |
|---|---|---|---|
| Cliente FRECUENTE (id 2), 2x Mouse | Corporativo 10% gana sobre Frecuente 4% | subtotal 100.000 → desc. 10% → 90.000 + 19% imp. = **107.100** | **107.100,0** ✓ |
| Cliente ESTANDAR (id 4), 21x Laptop | Volumen 12% (>20 unidades) | subtotal 25.200.000 → desc. 12% → 22.176.000 + 19% imp. = **26.389.440** | **26.389.440,0** ✓ |

Además, como `aplicarDescuentoCampana` solo conservaba el mayor valor entre las tres
campañas (operación conmutativa y asociativa), el orden de los `.encadenar(...)`
nunca podía alterar el resultado — ver la tabla de propiedades del punto 1, donde
ninguna de las tres clases dependía de ejecutarse antes o después de otra.

### 3. ¿Por qué una clase llamada `ValidadorPedido` contiene clases que nunca rechazan?

Porque las promociones solo reutilizan la infraestructura de la cadena
(`encadenar()` y `validar()`), no su semántica. El corte anticipado de
`ValidadorPedido.validar()` no tiene sentido para ellas:

```java
ejecutar(contexto);
if (!contexto.isRechazado() && siguiente != null) { siguiente.validar(contexto); }
```

El comentario de `PromocionBlackFriday` lo admite: "nunca rechaza - este eslabon
no valida nada". El nombre del tipo base miente: quien lee
`stock.encadenar(cliente).encadenar(blackFriday)...` en el constructor de
`GestorPedidos` cree que hay 5 validaciones, cuando 3 son cálculos de precio.

Hay además dos síntomas de que están en la etapa equivocada del flujo:
- Se ejecutan dentro de `primerValidador.validar(contexto)`, antes de
  `calcularSubtotal(request)` y `contexto.setSubtotal(...)`. Por eso
  `PromocionVolumen` no puede usar datos ya calculados y recalcula
  `totalUnidades` desde el request (lo dice su propio comentario).
- Se comunican con `GestorPedidos` mediante un efecto lateral: escriben en el
  campo mutable `descuentoCampana`, que `GestorPedidos` lee después con
  `Math.max(descuentoTipoCliente, contexto.getDescuentoCampana())`. Quien lee
  `procesarPedido()` no ve que la cadena produce un descuento.

### 4. ¿Qué pasaría si dos campañas tuvieran que combinarse (sumar en vez de tomar el máximo)?

La cadena no lo permite sin ambigüedad:
- La política "el mayor gana" está dentro de `ContextoPedido` y la comparten las
  tres campañas. Cambiarla para dos campañas obliga a modificar el contexto
  (afectando a todas) o a añadir banderas por campaña.
- La regla de combinación queda repartida en dos lugares: `aplicarDescuentoCampana`
  (entre campañas) y el `Math.max` de `GestorPedidos` (contra el descuento por
  tipo de cliente).
- Si aparecen topes, exclusiones o prioridades (p. ej. "Black Friday no se acumula
  con otras"), el orden de los `.encadenar(...)` pasaría a ser una regla de
  negocio implícita, aunque las clases se declararon sin dependencia de orden.

### 5. ¿Se eligió por adecuación al problema o porque "ya existía y funcionó"?

Por lo segundo. Evidencia:
- El comentario de `PromocionBlackFriday` dice que se agregó a la cadena porque
  "los eslabones ya sabian como conectarse entre si", y que solo "aprovecha que
  la cadena ya existe para engancharse".
- El commit `feat: agregar 3 campanas de descuento como eslabones de la cadena
  de validacion` describe el mecanismo elegido, no el problema resuelto.
- La herramienta adecuada ya existía en el mismo proyecto: `EstrategiaDescuento`
  (`double calcular(ContextoPedido)`) y sus implementaciones `DescuentoVip` y
  `DescuentoFrecuente`, que hacen exactamente lo mismo que las tres campañas
  (un porcentaje a partir de datos del pedido o del cliente, sin orden ni corte).

### Diagnóstico

**Golden Hammer.** Se reutilizó Chain of Responsibility, el patrón que resolvió la
Parte 1, en un problema con forma distinta: sin dependencia de orden, sin corte
anticipado y sin decisión de rechazo. La consecuencia es un tipo base
(`ValidadorPedido`) con un contrato roto, estado mutable compartido como canal
oculto de comunicación y una regla de combinación de descuentos dispersa. No se
evaluó si el nuevo problema tenía la misma forma que el anterior.

### Decisiones de Diseño (Parte 2)

#### Strategy en vez de más eslabones de Cadena
- **Justificación:** Se corrigió modelando las tres campañas promocionales como `EstrategiaDescuento` combinadas mediante `CalculadorDescuentoFinal`. De esta forma, calculan un porcentaje sin depender de un orden de ejecución ni manipular estado mutable compartido, preservando la semántica estricta de la cadena de validación `ValidadorPedido`.
- **Alternativa descartada:** Mantener las promociones como eslabones dentro de la cadena `ValidadorPedido` (o agregar métodos booleanos condicionales dentro del contexto). Fue descartada explícitamente por ser la causa del antipatrón *Golden Hammer*: reutilizar una herramienta conocida sin verificar que el nuevo problema tuviera su misma forma.

#### Eliminación directa del código descartado (Evitar Lava Flow)
- **Justificación:** Las clases `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen` del paquete `validacion` y el campo `descuentoCampana` del contexto fueron eliminados por completo en vez de dejarlos comentados.
- **Alternativa descartada:** Dejar las clases o bloques de código comentados "por si acaso se necesitan después". Se descartó porque es el mecanismo clásico que da origen al antipatrón *Lava Flow* (código muerto que nadie se atreve a borrar por incertidumbre). El historial de Git es el lugar correcto para conservar la referencia histórica.

## Cómo ejecutar
```
$ mvn spring-boot:run
$ mvn test
```

## Herramientas utilizadas
- Java 17, Spring Boot, Spring JDBC, Maven, H2 Database
- VS Code / IntelliJ IDEA, Git, GitHub

## Conclusiones

Este ejercicio me mostró que diagnosticar antes de refactorizar es tan importante como elegir el patrón: en la Parte 1, citar líneas, responsabilidades y niveles de anidamiento de `GestorPedidos` me permitió distinguir un God Object (la clase con seis razones para cambiar) de un Spaghetti Code (el método con anidamiento y estado compartido), y separar cada responsabilidad con el patrón que le correspondía: Chain of Responsibility para las validaciones, que sí tienen dependencia de orden y corte anticipado, y Strategy para el descuento, que solo depende del tipo de cliente. La Parte 2 me enseñó que un patrón que funcionó bien no se puede reutilizar por costumbre: las tres campañas encajaban como eslabones de la cadena solo en apariencia, ya que no tenían orden ni corte anticipado, y eso dejó un tipo base con un contrato roto y un campo mutable como canal oculto. Corregirlo con `EstrategiaDescuento` y `CalculadorDescuentoFinal`, y eliminar el código descartado en lugar de comentarlo para evitar un Lava Flow, confirmó que antes de aplicar una herramienta conocida hay que comprobar si el nuevo problema tiene la misma forma que el anterior. Finalmente, verificar que las salidas del sistema fueran equivalentes antes y después de cada refactorización me dio confianza para cambiar el diseño sin alterar el comportamiento.