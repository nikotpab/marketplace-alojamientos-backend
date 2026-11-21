-- SCRUM-116: el método de pago de prueba que eligió el huésped (CU-17), por ejemplo pm_card_visa. La reconciliación
-- repite el cobro con la misma clave y Stripe exige los mismos parámetros. Null = el predeterminado (STRIPE_METODO_PAGO).
ALTER TABLE pago ADD COLUMN metodo_pago varchar(60);
