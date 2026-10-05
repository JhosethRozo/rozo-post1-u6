# Post-contenido — Unidad 6: Diagnóstico y Refactorización de Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software (Sexto Semestre). Este proyecto contiene el diagnóstico y refactorización incremental de un sistema de gestión de pedidos (`pedidos-service`), identificando y corrigiendo antipatrones mediante patrones de diseño consolidados (SOLID, Chain of Responsibility, Strategy y separación en capas).

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

## Plan de Refactorización

Para eliminar el God Object y el Spaghetti Code, se desacoplará el método `procesarPedido` en componentes altamente cohesivos y débilmente acoplados:

| Responsabilidad Extraída | Patrón / Solución Arquitectónica | Justificación |
|---|---|---|
| **Secuencia de Validaciones (Stock, Cliente, Mora)** | **Chain of Responsibility** (`ValidadorPedido`) | Permite encadenamiento ordenado con **corte anticipado** (*fail-fast*): si el stock es insuficiente, se aborta inmediatamente sin consultar innecesariamente la mora del cliente en base de datos. |
| **Cálculo de Descuentos por Tipo de Cliente** | **Strategy** (`EstrategiaDescuento`) | Cada tipo de cliente (`VIP`, `FRECUENTE`, `ESTANDAR`) encapsula su propio algoritmo sin jerarquía de orden, seleccionado mediante un mapa directo (`SelectorEstrategiaDescuento`), cumpliendo OCP. |
| **Persistencia a Base de Datos** | **Repository Pattern** (`PedidoRepository`) | Encapsula las operaciones JDBC y sentencias SQL, aislando la lógica de negocio de la infraestructura relacional. |
| **Generación de Notificaciones** | **Dedicated Service** (`NotificacionPedidoService`) | Separa el ensamblado del mensaje de correo de la lógica transaccional de compras. |
| **Orquestación del Flujo** | **Orquestador Delgado** (`GestorPedidos`) | Coordina las capas en menos de 30 líneas sin conocer los detalles de implementación interna. |
