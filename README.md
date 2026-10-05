# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software — Sexto Semestre. Un único proyecto Spring Boot (`pedidos-service/`) estructurado en dos partes: diagnóstico y refactorización de un antipatrón combinado en `GestorPedidos` (Parte 1), y diagnóstico y corrección de un segundo antipatrón introducido al hacer crecer el mismo proyecto con tres campañas de descuento promocional (Parte 2).

---

## Estructura del Proyecto

```
rozo-post1-u6/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/tienda/pedidos/
    │   │   ├── PedidosServiceApplication.java
    │   │   ├── dto/
    │   │   │   ├── ItemPedido.java
    │   │   │   ├── PedidoRequest.java
    │   │   │   └── ResultadoPedido.java
    │   │   ├── validacion/
    │   │   │   ├── ContextoPedido.java
    │   │   │   ├── ValidadorCliente.java
    │   │   │   ├── ValidadorPedido.java
    │   │   │   └── ValidadorStock.java
    │   │   ├── descuento/
    │   │   │   ├── CalculadorDescuentoFinal.java
    │   │   │   ├── DescuentoBlackFriday.java
    │   │   │   ├── DescuentoCorporativo.java
    │   │   │   ├── DescuentoEstandar.java
    │   │   │   ├── DescuentoFrecuente.java
    │   │   │   ├── DescuentoVip.java
    │   │   │   ├── DescuentoVolumen.java
    │   │   │   ├── EstrategiaDescuento.java
    │   │   │   └── SelectorEstrategiaDescuento.java
    │   │   └── service/
    │   │       ├── EmailService.java
    │   │       ├── EmailServiceImpl.java
    │   │       ├── GestorPedidos.java
    │   │       ├── NotificacionPedidoService.java
    │   │       └── PedidoRepository.java
    │   └── resources/
    │       ├── application.properties
    │       ├── data.sql
    │       └── schema.sql
    └── test/
        └── java/com/tienda/pedidos/
            └── GestorPedidosTest.java
```

---

## Decisiones de diseño

### Parte 1 — GestorPedidos
- **Antipatrón identificado:** **God Object y Spaghetti Code combinados.**
  `GestorPedidos.procesarPedido()` mezclaba **6 responsabilidades dispares** (validación de stock en base de datos, validación de estado y corte horario del cliente moroso, cálculo de subtotal consultando precios de productos, cálculo de descuentos con hasta 3 niveles de anidamiento condicional, persistencia transaccional directa vía JDBC embebido y construcción de notificaciones por correo) en un único método de más de 100 líneas, dentro de una clase monolítica de 340 líneas en total. Esto violaba directamente el Principio de Responsabilidad Única (SRP) y el Principio Abierto/Cerrado (OCP).
- **Patrón aplicado:** 
  1. `Chain of Responsibility` para las validaciones (`ValidadorStock` y `ValidadorCliente`), aprovechando la **dependencia real de orden y la necesidad de corte anticipado (*fail-fast*)**: si no hay stock disponible, el pedido se rechaza inmediatamente sin consultar facturas ni mora del cliente.
  2. `Strategy` para el cálculo de descuento por tipo de cliente (`DescuentoVip`, `DescuentoFrecuente`, `DescuentoEstandar` gobernados por `SelectorEstrategiaDescuento`), donde no existe orden causal y siempre aplica exactamente una regla basada en la categoría del cliente.
  3. Extracción de capas cohesivas: `PedidoRepository` (anotado `@Repository`) para persistencia JDBC aislada y `NotificacionPedidoService` (anotado `@Service`) para formatear el mensaje. `GestorPedidos` quedó reducido a un orquestador delgado de 45 líneas.
- **Alternativa descartada:** Una lista de predicados booleanos (`List<Predicate<ContextoPedido>>`) para las validaciones. Fue descartada porque evalúa todas las condiciones aunque la primera ya haya fallado, careciendo de corte anticipado real y dificultando la propagación de mensajes de error contextuales de negocio.

---

### Parte 2 — Crecimiento del proyecto
- **Antipatrón identificado:** **Golden Hammer (Martillo de Oro / Ley del Instrumento).**
  Al añadir tres nuevas campañas promocionales (`BLACK_FRIDAY` 25%, `CORPORATIVO` 10%, `VOLUMEN` 12%), se implementaron inicialmente como eslabones adicionales de la cadena de validación existente (`PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen`), reutilizando `Chain of Responsibility` simplemente porque *"ya existía y había funcionado en la Parte 1"*. 
  Esto constituyó un Golden Hammer con evidencia concreta en el código:
  1. Las campañas promocionales **no tenían ninguna dependencia de orden entre sí** (ejecutar volumen antes que corporativo no altera el resultado).
  2. **Violaban el contrato de `ValidadorPedido`:** nunca rechazaban un pedido, sino que únicamente escribían en un campo mutable compartido (`descuentoCampana`) forzado artificialmente en `ContextoPedido`.
- **Patrón aplicado:** Se corrigió migrando las tres campañas a `EstrategiaDescuento` (`DescuentoBlackFriday`, `DescuentoCorporativo`, `DescuentoVolumen`) integradas mediante `CalculadorDescuentoFinal`. Esta clase combina la estrategia del tipo de cliente con las campañas activas tomando el mayor descuento, manteniendo inmutabilidad en el contexto.
- **Prevención de Lava Flow:** Los tres eslabones mal aplicados (`PromocionBlackFriday.java`, `PromocionCorporativo.java`, `PromocionVolumen.java`) y el campo `descuentoCampana` se **eliminaron por completo del repositorio mediante Git**, en lugar de dejarlos comentados *"por si acaso"*. Dejar código comentado genera código muerto (*Lava Flow*) que entorpece el mantenimiento futuro; su trazabilidad histórica quedó preservada adecuadamente en los commits de Git.

---

## Cómo ejecutar

### Prerrequisitos
- Java 17 o superior (`java -version`)
- Maven 3.8+ (`mvn -version`)

### Comandos de ejecución
```bash
# Compilar el proyecto
mvn compile

# Ejecutar las pruebas automatizadas (7 casos de negocio y promociones)
mvn test

# Iniciar la aplicación Spring Boot
mvn spring-boot:run
```

- **Consola H2 Database:** `http://localhost:8080/h2-console`
  - **JDBC URL:** `jdbc:h2:mem:pedidos_db`
  - **Usuario:** `sa`
  - **Contraseña:** *(dejar vacío)*

---

## Verificación de Casos de Prueba (Antes y Después)

La salida del sistema refactorizado es exactamente equivalente a la versión original para todos los casos de prueba:

| Caso de Prueba | Entrada | Salida Observable |
|---|---|---|
| **1. Stock Insuficiente** | Producto 101, Cantidad 99 | Rechazado: `"Stock insuficiente: producto 101"` |
| **2. Cliente Inexistente** | Cliente 9999 | Rechazado: `"Cliente no registrado"` |
| **3. Cliente Moroso** | Cliente 4, Deuda $150.000 (< 20:00) | Rechazado: `"Cliente con deuda pendiente: $150000.0"` |
| **4. Black Friday Activo** | Cliente 3 (ESTANDAR), Subtotal $200.000 | Confirmado: Descuento 25%, Total `$178.500.0` |
| **5. Cliente Corporativo** | Cliente 5 (NIT registrado), Subtotal $50.000 | Confirmado: Descuento 25% (mayor entre 10% y 25%), Total `$44.625.0` |
| **6. Descuento por Volumen** | 25 unidades de Mouse, Subtotal $1.250.000 | Confirmado: Descuento 25% (mayor entre 12% y 25%), Total `$1.115.625.0` |
| **7. Cliente VIP con Campaña** | Cliente 1 (VIP), Subtotal $1.200.000 | Confirmado: Descuento 25% (mayor entre 15% VIP y 25% promo), Total `$1.071.000.0` |

---

## Herramientas utilizadas
- **Lenguaje:** Java 17 (OpenJDK / JetBrains Runtime)
- **Framework:** Spring Boot 3.2.5, Spring JDBC API (`JdbcTemplate`)
- **Base de Datos:** H2 Database (embebida en memoria con schema y datos precargados)
- **Construcción y Testing:** Apache Maven 3.9+, JUnit 5, Spring Boot Test
- **Control de Versiones:** Git & GitHub

---

## Conclusiones
La refactorización desarrollada en este laboratorio evidenció que la calidad arquitectónica de un sistema no depende únicamente de hacer que el código funcione, sino de ubicar cada responsabilidad en el patrón y la capa que corresponden a su naturaleza. La Parte 1 demostró el poder de desacoplar un *God Object* y desenredar *Spaghetti Code* combinando *Chain of Responsibility* para procesos con corte anticipado y *Strategy* para decisiones policéntricas independientes. Asimismo, la Parte 2 aportó el aprendizaje más valioso: identificar que los patrones de diseño no deben aplicarse por inercia; forzar un problema a la forma de una solución previa engendra el antipatrón *Golden Hammer*, y corregirlo a tiempo eliminando el código sobrante previene la acumulación de deuda técnica y *Lava Flow*.
