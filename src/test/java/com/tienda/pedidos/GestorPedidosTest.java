package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GestorPedidosTest {

    @Autowired
    private GestorPedidos gestorPedidos;

    @Test
    @DisplayName("Caso 1: Stock insuficiente - debe rechazar el pedido")
    void testStockInsuficiente() {
        PedidoRequest request = new PedidoRequest(1L, "carlos@vip.com",
                List.of(new ItemPedido(101L, 99)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertFalse(resultado.isConfirmado());
        assertNotNull(resultado.getMotivoRechazo());
        assertTrue(resultado.getMotivoRechazo().contains("Stock insuficiente"));
    }

    @Test
    @DisplayName("Caso 2: Cliente no registrado - debe rechazar el pedido")
    void testClienteInexistente() {
        PedidoRequest request = new PedidoRequest(9999L, "desconocido@correo.com",
                List.of(new ItemPedido(104L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    @DisplayName("Caso 3: Cliente moroso con deuda pendiente")
    void testClienteMoroso() {
        PedidoRequest request = new PedidoRequest(4L, "andres@moroso.com",
                List.of(new ItemPedido(104L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        if (LocalTime.now().isBefore(LocalTime.of(20, 0))) {
            assertFalse(resultado.isConfirmado());
            assertTrue(resultado.getMotivoRechazo().contains("Cliente con deuda pendiente"));
        } else {
            assertTrue(resultado.isConfirmado());
        }
    }

    @Test
    @DisplayName("Caso 4: Campaña Black Friday activa (25% descuento)")
    void testCampanaBlackFriday() {
        // Cliente 3 (ESTANDAR, sin descuento propio); compra 1 Teclado ($200.000)
        // Black Friday activo (25%) -> Descuento = $50.000
        // Base = $150.000; Impuesto 19% = $28.500; Total = $178.500
        PedidoRequest request = new PedidoRequest(3L, "pedro@estandar.com",
                List.of(new ItemPedido(103L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(178500.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Caso 5: Campaña Cliente Corporativo con NIT (10% descuento base)")
    void testCampanaCorporativo() {
        // Cliente 5 posee NIT '800987654-3'.
        // Con Black Friday activo aplica el mayor (25% vs 10% -> 25%)
        // Subtotal: 1 Mouse ($50.000). Descuento 25% = 12.500. Base = 37.500. IVA 19% = 7.125. Total = 44.625
        PedidoRequest request = new PedidoRequest(5L, "empresa@abc.com",
                List.of(new ItemPedido(104L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(44625.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Caso 6: Campaña Volumen (> 20 unidades)")
    void testCampanaVolumen() {
        // Compra 25 unidades de Mouse (104) a $50.000 = $1.250.000 subtotal.
        // Supera 20 unidades -> califica para volumen (12%).
        // Con Black Friday activo (25% vs 12%), gana Black Friday (25%).
        // Descuento 25% = 312.500. Base = 937.500. IVA 19% = 178.125. Total = 1.115.625
        PedidoRequest request = new PedidoRequest(3L, "volumen@test.com",
                List.of(new ItemPedido(104L, 25)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(1115625.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Caso 7: Cliente VIP con subtotal > 1M y combinación de campañas (el mayor descuento gana)")
    void testClienteVIPConCampana() {
        // Cliente 1 (VIP) con subtotal $1.200.000 (15% VIP).
        // Black Friday otorga 25%.
        // CalculadorDescuentoFinal toma Math.max(0.15, 0.25) = 25%.
        // Subtotal = 1.200.000; Descuento = 300.000; Base = 900.000; IVA 19% = 171.000; Total = 1.071.000
        PedidoRequest request = new PedidoRequest(1L, "carlos@vip.com",
                List.of(new ItemPedido(101L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(1071000.0, resultado.getTotal(), 0.01);
    }
}
