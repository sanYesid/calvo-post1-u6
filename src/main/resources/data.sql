
-- Productos
INSERT INTO productos (id, nombre, precio) VALUES (1, 'Laptop', 1200000.0);
INSERT INTO productos (id, nombre, precio) VALUES (2, 'Mouse', 50000.0);

-- Clientes (VIP, FRECUENTE, MOROSO, ESTANDAR)
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (1, 'Juan VIP', 'VIP', '900123456-1');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (2, 'Maria Frecuente', 'FRECUENTE', '800987654-3');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (3, 'Carlos Moroso', 'MOROSO', NULL);
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (4, 'Ana Estandar', 'ESTANDAR', NULL);

-- Inventario
INSERT INTO inventario (producto_id, stock) VALUES (1, 100);
INSERT INTO inventario (producto_id, stock) VALUES (2, 5);

-- Facturas pendientes para moroso
INSERT INTO facturas (id, cliente_id, monto, pagada) VALUES (1, 3, 150000.0, false);

-- Historial para cliente frecuente (se omiten los IDs explícitos para no romper la secuencia AUTO_INCREMENT)
-- OJO: son exactamente 4 pedidos previos. DescuentoFrecuente exige > 3 para el 4%
-- (y > 10 para el 8%). Si se agregan o quitan filas aquí, testDescuentoCorporativo
-- deja de comparar "Corporativo 10% vs Frecuente 4%" y hay que revisar ese test.
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP(), 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP(), 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP(), 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES (2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP(), 'CONFIRMADO');