package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GestorPedidosMorosoTest {

    private static PedidoRequest pedidoDeCarlosMoroso() {
        ItemPedido item = new ItemPedido();
        item.setProductoId(2L); // Mouse, stock=5 en data.sql
        item.setCantidad(1);
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(3L); // Carlos Moroso, con factura pendiente en data.sql
        request.setClienteEmail("moroso@tienda.com");
        request.setItems(List.of(item));
        return request;
    }

    @SpringBootTest(properties = "promo.black-friday.activa=false")
    @Nested
    class DentroDelHorarioDeCorte {

       @TestConfiguration
static class ClockFijoA15h {
    @Bean
    @Primary
    Clock clockDePruebaDentroDeHorario() {
        return Clock.fixed(
            LocalDate.now().atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());
    }
}

        @Autowired
        private GestorPedidos gestorPedidos;

        @Test
        @DisplayName("Moroso con deuda, antes de las 20:00: se rechaza")
        void testMorosoDentroDeHorarioSeRechaza() {
            ResultadoPedido resultado = gestorPedidos.procesarPedido(pedidoDeCarlosMoroso());

            assertFalse(resultado.isConfirmado());
            assertTrue(resultado.getMotivoRechazo().contains("deuda pendiente"));
        }
    }

    @SpringBootTest(properties = "promo.black-friday.activa=false")
    @Nested
    class FueraDelHorarioDeCorte {

        @TestConfiguration
static class ClockFijoA21h {
    @Bean
    @Primary
    Clock clockDePruebaFueraDeHorario() {
        return Clock.fixed(
            LocalDate.now().atTime(21, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());
    }
}

        @Autowired
        private GestorPedidos gestorPedidos;

        @Test
        @DisplayName("Moroso con deuda, después de las 20:00: se permite excepcionalmente")
        void testMorosoFueraDeHorarioSeConfirma() {
            ResultadoPedido resultado = gestorPedidos.procesarPedido(pedidoDeCarlosMoroso());

            assertTrue(resultado.isConfirmado());
        }
    }
}