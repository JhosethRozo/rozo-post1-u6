# Post-contenido — Unidad 6: Diagnóstico y Refactorización de Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software (Sexto Semestre). Este proyecto contiene el diagnóstico y refactorización incremental de un sistema de gestión de pedidos (`pedidos-service`), identificando y corrigiendo antipatrones mediante patrones de diseño consolidados (SOLID, Chain of Responsibility, Strategy y separación en capas).

---

## Cómo ejecutar

### Prerrequisitos
- Java 17 o superior (`java -version`)
- Maven 3.8+ (`mvn -version`)

### Comandos de ejecución
```bash
# Compilar el proyecto
mvn compile

# Ejecutar las pruebas automatizadas (5 casos de negocio)
mvn test

# Iniciar la aplicación Spring Boot
mvn spring-boot:run
```

- Consola H2: `http://localhost:8080/h2-console`
  - **JDBC URL:** `jdbc:h2:mem:pedidos_db`
  - **Usuario:** `sa`
  - **Contraseña:** *(vacía)*

---

## Diagnóstico de Antipatrones — Parte 1: GestorPedidos

### 1. Antipatrón: God Object (Clase Dios / Blob)
La clase `GestorPedidos` asume una concentración desmedida de responsabilidades que pertenecen a dominios completamente dispares del sistema, violando directamente el **Principio de Responsabilidad Única (SRP)** de SOLID.

#### Evidencia concreta en el código:
En el método único `procesarPedido(PedidoRequest request)` conviven **6 responsabilidades críticas** mezcladas de forma monolítica:
1. **Validación de Inventario / Stock:** Comprueba la existencia de items y realiza consultas directas a base de datos sobre la tabla `inventario` para verificar disponibilidad.
2. **Validación de Estado del Cliente y Regla de Corte:** Consulta la tabla `clientes`, valida la existencia del cliente, verifica facturas impagas en la tabla `facturas` para clientes morosos y evalúa una excepción basada en la hora del sistema (`LocalTime.now() < 20:00`).
3. **Cálculo de Precios y Subtotal:** Itera sobre los items y consulta la tabla `productos` para obtener precios unitarios y acumular el subtotal monetario.
4. **Lógica de Descuentos Promocionales:** Aplica reglas condicionales de negocio dependientes de categorías de cliente (`VIP`, `FRECUENTE`) y montos acumulados.
5. **Persistencia Transaccional Directa vía JDBC:** Inserta directamente en la tabla `pedidos`, recupera el ID generado mediante `CALL IDENTITY()`, inserta en `detalle_pedido` y actualiza el stock en la tabla `inventario`.
6. **Construcción y Envío de Notificaciones:** Formatea manualmente el cuerpo del correo en texto plano línea por línea e invoca `EmailService`.

**Razones para cambiar:** Esta clase tiene al menos **6 motivos independientes de cambio** (si cambia la regla de inventario, si cambia la política de morosidad, si cambia la estructura de la base de datos SQL, si cambia el cálculo de descuentos, si cambia el formato de la factura/correo o si cambia el proveedor de persistencia).

---

### 2. Antipatrón: Spaghetti Code (Código Espagueti)
El flujo de control dentro de `procesarPedido` es enredado, carece de cohesión modular y mezcla múltiples niveles de abstracción conceptual en una sola secuencia lineal.

#### Evidencia concreta en el código:
- **Disparidad de niveles de abstracción simultáneos:** En una misma secuencia de líneas, el código salta desde SQL crudo (`"SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false"`), pasando por lógica temporal (`LocalTime.now().isBefore(LocalTime.of(20, 0))`), hasta formateo de cadenas para emails (`cuerpo.append("Subtotal: $").append(subtotal)`).
- **Anidamiento condicional profundo (High Cyclomatic Complexity):** El bloque de cálculo de descuentos alcanza hasta **3 niveles de anidamiento** con ramificaciones disjuntas:
  ```java
  if (tipoCliente.equals("VIP")) {
      if (subtotal > 1_000_000) {
          descuento = 0.15;
      } else if (subtotal > 500_000) { ... }
  } else if (tipoCliente.equals("FRECUENTE")) {
      Integer pedidosPrevios = ...
      if (pedidosPrevios != null && pedidosPrevios > 10) { ... }
  }
  ```
- **Violación del Principio Abierto/Cerrado (OCP):** Si mañana el negocio introduce un nuevo tipo de cliente (por ejemplo, `CORPORATIVO` o `DOCENTE`), es forzoso modificar el código fuente de `GestorPedidos`, alterando sentencias SQL embebidas, reescribiendo condiciones y arriesgando la estabilidad del cálculo de clientes existentes.
- **Inexistencia de límites transaccionales formales:** Si la inserción en `detalle_pedido` falla tras haber insertado en `pedidos`, la base de datos queda en un estado inconsistente debido a la ausencia de abstracción de repositorios y transacciones.

---

## Decisiones de diseño — Parte 1

### Decisión 1: Validación como Chain of Responsibility
- **Patrón Aplicado:** `Chain of Responsibility` mediante la clase abstracta `ValidadorPedido` y los eslabones concretos `ValidadorStock` y `ValidadorCliente`.
- **Justificación:** Las validaciones de un pedido poseen una **dependencia real de orden y necesidad de corte anticipado (*fail-fast*)**: si no hay stock disponible, el pedido debe ser rechazado inmediatamente sin incurrir en el costo de consultar la base de datos para examinar el tipo y la mora del cliente.
- **Alternativa Descartada:** Se evaluó utilizar una lista de predicados booleanos (`List<Predicate<ContextoPedido>>`). Dicha alternativa fue descartada porque un enfoque de predicados itera y evalúa todas las condiciones aunque la primera ya haya fallado, sin proporcionar un mecanismo limpio para abortar tempranamente el flujo de ejecución ni para transmitir un motivo de rechazo contextual enriquecido de manera desacoplada.

### Decisión 2: Descuento como Strategy y no como parte de la cadena
- **Patrón Aplicado:** `Strategy` a través de la interfaz `EstrategiaDescuento` con implementaciones concretas (`DescuentoVip`, `DescuentoFrecuente`, `DescuentoEstandar`) y un despachador `SelectorEstrategiaDescuento`.
- **Justificación:** A diferencia de las validaciones, las reglas de descuento por tipo de cliente no tienen una relación secuencial de orden ni requieren cortar el flujo. Se aplica exactamente una estrategia basada en el tipo de cliente. Encapsular cada cálculo en su propia clase permite agregar nuevos tipos de cliente respetando el Principio Abierto/Cerrado (OCP) sin alterar código existente.
- **Alternativa Descartada:** Se descartó modelar los descuentos como eslabones de la cadena de validación porque obligaría a forzar un mecanismo artificial para evitar que múltiples eslabones sobrescriban o compitan por el valor de descuento, agregando indirección y acoplamiento innecesario.

### Decisión 3: Extracción de Persistencia y Notificación
- **Solución:** Se extrajo `PedidoRepository` (anotado con `@Repository`) para encapsular las sentencias SQL JDBC y operaciones relacionales, y `NotificacionPedidoService` (anotado con `@Service`) para la construcción y envío del correo.
- **Resultado:** `GestorPedidos` pasa de ser una clase de 340 líneas con 6 responsabilidades a un **orquestador delgado de 45 líneas** altamente legible y enfocado únicamente en coordinar las capas.

---

## Verificación de Comportamiento Observable

Los cinco casos de prueba de negocio producen **exactamente la misma salida y totales** antes y después de la refactorización:

| Caso de Prueba | Entrada | Salida Observable (Antes y Después) |
|---|---|---|
| **1. Stock Insuficiente** | Producto 101, Cantidad 99 | Rechazado: `"Stock insuficiente: producto 101"` |
| **2. Cliente Inexistente** | Cliente 9999, Cantidad 1 | Rechazado: `"Cliente no registrado"` |
| **3. Cliente Moroso** | Cliente 4, Deuda $150.000 (< 20:00) | Rechazado: `"Cliente con deuda pendiente: $150000.0"` |
| **4. Cliente VIP (> 1M)** | Cliente 1, Subtotal $1.200.000 | Confirmado: Descuento 15%, Total `$1.213.800.0` |
| **5. Cliente Frecuente** | Cliente 2, 5 pedidos previos, Subtotal $400.000 | Confirmado: Descuento 4%, Total `$456.960.0` |
