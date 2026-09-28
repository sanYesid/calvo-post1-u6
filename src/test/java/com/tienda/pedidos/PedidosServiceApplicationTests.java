package com.tienda.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "promo.black-friday.activa=true")
class PedidosServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}