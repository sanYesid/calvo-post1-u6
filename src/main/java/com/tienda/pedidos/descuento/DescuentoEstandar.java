package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

@org.springframework.stereotype.Component
public class DescuentoEstandar implements EstrategiaDescuento {
    @Override
    public double calcular(ContextoPedido contexto) { return 0.0; }
}