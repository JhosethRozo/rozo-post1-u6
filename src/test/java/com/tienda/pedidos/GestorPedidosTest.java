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
        // Producto 101 tiene stock 10 en inventario; se piden 99 unidades
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
        // Cliente 9999 no existe en la base de datos
        PedidoRequest request = new PedidoRequest(9999L, "desconocido@correo.com",
                List.of(new ItemPedido(104L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    @DisplayName("Caso 3: Cliente moroso con deuda pendiente")
    void testClienteMoroso() {
        // Cliente 4 es MOROSO con deuda de $150000.0 en facturas
        PedidoRequest request = new PedidoRequest(4L, "andres@moroso.com",
                List.of(new ItemPedido(104L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        if (LocalTime.now().isBefore(LocalTime.of(20, 0))) {
            assertFalse(resultado.isConfirmado());
            assertTrue(resultado.getMotivoRechazo().contains("Cliente con deuda pendiente"));
        } else {
            // Fuera de horario de corte (excepción nocturna)
            assertTrue(resultado.isConfirmado());
        }
    }

    @Test
    @DisplayName("Caso 4: Cliente VIP con subtotal > 1M (15% descuento)")
    void testClienteVIPDescuentoMaximo() {
        // Cliente 1 es VIP; compra 1 Laptop (101) a $1.200.000 -> subtotal > 1.000.000 -> 15% desc.
        // Subtotal: 1.200.000
        // Descuento: 180.000
        // Base: 1.020.000
        // Impuesto (19%): 193.800
        // Total esperado: 1.213.800
        PedidoRequest request = new PedidoRequest(1L, "carlos@vip.com",
                List.of(new ItemPedido(101L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(1213800.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Caso 5: Cliente FRECUENTE con > 3 pedidos previos (4% descuento)")
    void testClienteFrecuenteDescuento() {
        // Cliente 2 es FRECUENTE con 5 pedidos previos en data.sql -> 4% descuento
        // Compra 2 Teclados (103) a $200.000 c/u = $400.000 subtotal
        // Descuento (4%): 16.000
        // Base: 384.000
        // Impuesto (19%): 72.960
        // Total esperado: 456.960
        PedidoRequest request = new PedidoRequest(2L, "laura@frecuente.com",
                List.of(new ItemPedido(103L, 2)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(456960.0, resultado.getTotal(), 0.01);
    }
}
