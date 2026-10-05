package com.tienda.pedidos.validacion;

// Eslabon de la cadena: cada validador decide si el pedido continua o se rechaza
public abstract class ValidadorPedido {
    private ValidadorPedido siguiente;

    public ValidadorPedido encadenar(ValidadorPedido siguiente) {
        if (this.siguiente == null) {
            this.siguiente = siguiente;
        } else {
            this.siguiente.encadenar(siguiente);
        }
        return this;
    }

    public final void validar(ContextoPedido contexto) {
        ejecutar(contexto);
        if (!contexto.isRechazado() && siguiente != null) {
            siguiente.validar(contexto);
        }
    }

    protected abstract void ejecutar(ContextoPedido contexto);
}
