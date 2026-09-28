package com.tienda.pedidos.descuento;

@org.springframework.stereotype.Component
public class DescuentoBlackFriday implements EstrategiaDescuento {
    private final boolean campanaActiva;

    public DescuentoBlackFriday(@org.springframework.beans.factory.annotation.Value("${promo.black-friday.activa}") boolean campanaActiva) {
        this.campanaActiva = campanaActiva;
    }

    @Override
    public double calcular(com.tienda.pedidos.validacion.ContextoPedido contexto) {
        return campanaActiva ? 0.25 : 0.0;
    }
}
