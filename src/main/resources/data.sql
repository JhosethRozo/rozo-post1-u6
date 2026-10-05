DELETE FROM detalle_pedido;
DELETE FROM pedidos;
DELETE FROM facturas;
DELETE FROM inventario;
DELETE FROM productos;
DELETE FROM clientes;

-- Clientes
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (1, 'Carlos VIP', 'VIP', '900123456-1');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (2, 'Laura Frecuente', 'FRECUENTE', '');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (3, 'Pedro Estandar', 'ESTANDAR', '');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (4, 'Andres Moroso', 'MOROSO', '');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (5, 'Corporación ABC', 'ESTANDAR', '800987654-3');

-- Factura pendiente para cliente moroso
INSERT INTO facturas (cliente_id, monto, pagada) VALUES (4, 150000.0, false);

-- Pedidos previos para cliente frecuente (id 2 tiene 5 pedidos previos -> descuento 4%)
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO');

-- Productos
INSERT INTO productos (id, nombre, precio) VALUES (101, 'Laptop Profesional', 1200000.0);
INSERT INTO productos (id, nombre, precio) VALUES (102, 'Monitor 4K', 600000.0);
INSERT INTO productos (id, nombre, precio) VALUES (103, 'Teclado Mecánico', 200000.0);
INSERT INTO productos (id, nombre, precio) VALUES (104, 'Mouse Gamer', 50000.0);

-- Inventario inicial
INSERT INTO inventario (producto_id, stock) VALUES (101, 10);
INSERT INTO inventario (producto_id, stock) VALUES (102, 20);
INSERT INTO inventario (producto_id, stock) VALUES (103, 50);
INSERT INTO inventario (producto_id, stock) VALUES (104, 100);
