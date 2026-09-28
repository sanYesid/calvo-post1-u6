package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "promo.black-friday.activa=false")
class GestorPedidosTest {

    @Autowired
    private GestorPedidos gestorPedidos;

    private ItemPedido crearItem(Long productoId, int cantidad) {
        ItemPedido item = new ItemPedido();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        return item;
    }

    private PedidoRequest crearRequest(Long clienteId, String email, List<ItemPedido> items) {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(clienteId);
        request.setClienteEmail(email);
        request.setItems(items);
        return request;
    }

    @Test
    @DisplayName("Ruta 1: Rechazo por stock insuficiente")
    void testStockInsuficiente() {
        PedidoRequest request = crearRequest(1L, "vip@tienda.com", List.of(crearItem(2L, 10)));
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        
        assertFalse(resultado.isConfirmado());
        assertTrue(resultado.getMotivoRechazo().contains("Stock insuficiente"));
    }

    @Test
    @DisplayName("Ruta 2: Rechazo por cliente no existente")
    void testClienteInexistente() {
        PedidoRequest request = crearRequest(999L, "desconocido@tienda.com", List.of(crearItem(1L, 1)));
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        
        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    @DisplayName("Ruta 3: Descuento por Tipo Cliente VIP (15%)")
    void testDescuentoClienteVip() {
        PedidoRequest request = crearRequest(1L, "vip@tienda.com", List.of(crearItem(1L, 1)));
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        
        assertTrue(resultado.isConfirmado());
        assertEquals(1213800.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 4: Descuento por Campaña Corporativo (10% NIT vs 4% Frecuente)")
    void testDescuentoCorporativo() {
        PedidoRequest request = crearRequest(2L, "frecuente@tienda.com", List.of(crearItem(2L, 2)));
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        
        assertTrue(resultado.isConfirmado());
        assertEquals(107100.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 5: Descuento por Campaña Volumen (>20 unidades -> 12%)")
    void testDescuentoVolumen() {
        PedidoRequest request = crearRequest(4L, "estandar@tienda.com", List.of(crearItem(1L, 21)));
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        
        assertTrue(resultado.isConfirmado());
        assertEquals(26389440.0, resultado.getTotal(), 0.01);
    }
}