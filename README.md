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

# Ejecutar las pruebas automatizadas (casos de negocio y promociones)
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

## Diagnóstico de Antipatrones — Parte 2: El Crecimiento del Sistema

### 3. Antipatrón: Golden Hammer (Martillo de Oro / Ley del Instrumento)
Al incorporar las tres nuevas campañas de descuento solicitadas por mercadeo (`BLACK_FRIDAY`, `CORPORATIVO`, `VOLUMEN`), se incurrió en el antipatrón **Golden Hammer**: tomar una herramienta o patrón de diseño que funcionó exitosamente en una etapa anterior (`Chain of Responsibility`) y aplicarla a ciegas a un problema nuevo que tiene una naturaleza completamente distinta.

#### Evidencia concreta en el código:
1. **Ausencia total de dependencia de orden:** 
   - En la cadena de validación genuina, `ValidadorStock` debe ejecutarse forzosamente antes que `ValidadorCliente` porque si no hay stock no tiene sentido consultar la mora. 
   - En contraste, entre `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen` **no existe ninguna dependencia de orden**: ejecutar `PromocionVolumen` antes que `PromocionCorporativo` produce exactamente el mismo resultado.
2. **Ausencia de corte anticipado (*fail-fast*):**
   - El contrato semántico fundamental de `ValidadorPedido` es decidir si el pedido continúa o se rechaza (`contexto.rechazar(...)`).
   - Sin embargo, las tres clases de promoción **nunca rechazan un pedido**. Su única acción es mutar un campo compartido (`contexto.aplicarDescuentoCampana(...)`).
3. **Contaminación del Contexto con Estado Mutable Compartido:**
   - Para encajar las promociones a la fuerza en la cadena, se tuvo que ensuciar `ContextoPedido` agregándole `descuentoCampana` y un método mutable `aplicarDescuentoCampana(double valor)` con lógica implícita de negocio (`if (valor > this.descuentoCampana) ...`).
   - Si mañana se requiere sumar descuentos en vez de calcular el máximo, o si una campaña anula a otra, la cadena se vuelve ambigua e inmanejable.
4. **Motivación del error:** Se eligió `Chain of Responsibility` no porque fuera la solución técnicamente idónea, sino simplemente porque *"los eslabones ya sabían cómo conectarse entre sí"* y *"ya funcionó en la Parte 1"*.

---

## Decisiones de diseño — Parte 1 y Parte 2

### Parte 1: Chain of Responsibility y Strategy
- **Chain of Responsibility (`ValidadorPedido`):** Reservado estrictamente para los eslabones que poseen dependencia causal de orden y corte anticipado (`ValidadorStock` y `ValidadorCliente`).
- **Strategy (`EstrategiaDescuento`):** Encapsula el cálculo de descuento por tipo de cliente (`VIP`, `FRECUENTE`, `ESTANDAR`) sin acoplamiento de orden ni ramificaciones `if/else`.
- **Alternativa Descartada:** Predicados booleanos en lista (evalúan todo sin corte anticipado).

### Parte 2: Extensión de Strategy y Erradicación de Golden Hammer
- **Patrón Aplicado:** Se migran las 3 campañas de descuento al patrón **Strategy**, implementando `EstrategiaDescuento` en:
  - `DescuentoBlackFriday` (25% fijo si la propiedad está activa).
  - `DescuentoCorporativo` (10% si el cliente posee NIT registrado).
  - `DescuentoVolumen` (12% si la cantidad total supera 20 unidades).
- **CalculadorDescuentoFinal:** Clase cohesiva que inyecta el `SelectorEstrategiaDescuento` y la lista de campañas promocionales, computando limpiamente el mayor descuento sin campos mutables en el contexto ni cadenas artificiales.
- **Prevención de Lava Flow:** Se eliminaron por completo las clases `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen`, así como el campo `descuentoCampana` de `ContextoPedido`. Dejarlas comentadas en el código fuente habría creado código muerto (*Lava Flow*); su histórico se preserva adecuadamente en los commits de Git.
