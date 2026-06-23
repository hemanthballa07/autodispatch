INSERT INTO riders (id, name, phone, created_at)
VALUES ('00000000-0000-0000-0000-000000000010', 'Test Rider',
        '+919990000001', NOW());

INSERT INTO drivers (id, name, whatsapp_id, vehicle_no, status, verified, suspended, created_at)
VALUES ('00000000-0000-0000-0000-000000000020', 'Test Driver', '+919990000002',
        'KA01AB1234', 'ON_RIDE', true, false, NOW());

INSERT INTO rides (id, rider_id, driver_id, pickup_label, drop_label, status,
                   fare_amount, requested_at, assigned_at, version)
VALUES ('00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000010',
        '00000000-0000-0000-0000-000000000020',
        'Gate A', 'Hostel B', 'IN_PROGRESS',
        45.00, NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '8 minutes', 0);
