ALTER TABLE customer_order ADD CONSTRAINT order_shipment_required
    CHECK (status NOT IN ('SHIPPED','DELIVERED') OR tracking_number IS NOT NULL);
ALTER TABLE customer_order ADD CONSTRAINT order_tracking_number_format
    CHECK (tracking_number IS NULL OR tracking_number ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,99}$');
CREATE INDEX order_employee_list_idx ON customer_order(created_at DESC,id);

CREATE TABLE order_fulfillment_operation (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES customer_order(id),
    actor_account_id uuid NOT NULL REFERENCES app_account(id),
    kind varchar(10) NOT NULL CHECK (kind IN ('STATUS','SHIPMENT')),
    from_status varchar(30) NOT NULL,
    to_status varchar(30) NOT NULL,
    requested_tracking_number varchar(100),
    expected_tracking_number varchar(100),
    resulting_tracking_number varchar(100),
    created_at timestamptz NOT NULL,
    CHECK (
        (kind='STATUS' AND expected_tracking_number IS NULL AND (
            (from_status='PAID' AND to_status='PROCESSING' AND requested_tracking_number IS NULL AND resulting_tracking_number IS NULL)
            OR (from_status='PROCESSING' AND to_status='SHIPPED' AND requested_tracking_number IS NOT NULL AND resulting_tracking_number=requested_tracking_number)
            OR (from_status='SHIPPED' AND to_status='DELIVERED' AND requested_tracking_number IS NULL AND resulting_tracking_number IS NOT NULL)))
        OR (kind='SHIPMENT' AND from_status='SHIPPED' AND to_status='SHIPPED'
            AND expected_tracking_number IS NOT NULL AND requested_tracking_number IS NOT NULL
            AND resulting_tracking_number=requested_tracking_number)
    )
);
CREATE INDEX order_fulfillment_order_idx ON order_fulfillment_operation(order_id,created_at,id);
