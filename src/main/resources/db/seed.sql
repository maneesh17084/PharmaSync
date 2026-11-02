-- PharmaSync master data for a fresh database. Idempotent (INSERT OR IGNORE on unique keys).
-- Users, batches, purchases and sales are seeded by DemoDataSeeder so dates stay relative to today.

INSERT OR IGNORE INTO categories (name, description) VALUES
    ('Analgesics', 'Pain relief and fever'),
    ('Antibiotics', 'Antibacterial agents'),
    ('Antihistamines', 'Allergy medicines'),
    ('Vitamins', 'Vitamins and supplements'),
    ('Cough & Cold', 'Cough syrups and cold remedies'),
    ('Gastrointestinal', 'Antacids, PPIs and rehydration'),
    ('Antidiabetic', 'Oral hypoglycaemics and insulin'),
    ('Cardiovascular', 'Blood pressure and heart medicines'),
    ('Medical Supplies', 'Consumables and surgical supplies');

INSERT OR IGNORE INTO suppliers (name, contact_person, phone, email, address, state, gstin, drug_license_no) VALUES
    ('Apex Pharma Distributors', 'Ravi Kumar', '+91 98480 11111', 'orders@apexpharma.example', 'Secunderabad, Telangana', '36', '36AAACA1111A1Z1', 'DL-TS-1001'),
    ('Sunrise Healthcare Agencies', 'Meena Iyer', '+91 98480 22222', 'sales@sunrisehc.example', 'Vijayawada, Andhra Pradesh', '37', '37AAACS2222B1Z2', 'DL-AP-2002'),
    ('National Medico Supplies', 'Arjun Mehta', '+91 98480 33333', 'contact@nationalmedico.example', 'Mumbai, Maharashtra', '27', '27AAACN3333C1Z3', 'DL-MH-3003');

INSERT OR IGNORE INTO customers (name, customer_type, contact_person, phone, email, address, state, gstin, drug_license_no) VALUES
    ('Care Plus Pharmacy', 'PHARMACY', 'Suresh Reddy', '+91 90000 10001', 'careplus@example.com', 'Kukatpally, Hyderabad', '36', '36AAAFC1001A1Z1', 'RL-1001'),
    ('City General Hospital', 'HOSPITAL', 'Dr. Anitha Rao', '+91 90000 10002', 'purchase@citygeneral.example', 'Banjara Hills, Hyderabad', '36', '36AAAFC1002A1Z2', 'HL-2002'),
    ('Wellness Medicals', 'PHARMACY', 'Kiran Varma', '+91 90000 10003', 'wellness@example.com', 'Ameerpet, Hyderabad', '36', '36AAAFW1003A1Z3', 'RL-1003'),
    ('Sri Sai Clinic', 'CLINIC', 'Dr. Prakash', '+91 90000 10004', 'srisai@example.com', 'Madhapur, Hyderabad', '36', NULL, 'CL-3004'),
    ('Lifeline Multispeciality Hospital', 'HOSPITAL', 'Ramesh Gupta', '+91 90000 10005', 'stores@lifeline.example', 'Gachibowli, Hyderabad', '36', '36AAAFL1005A1Z5', 'HL-2005'),
    ('Coastal Pharma Traders', 'PHARMACY', 'Venkat Rao', '+91 90000 10006', 'orders@coastalpharma.example', 'Dwaraka Nagar, Visakhapatnam', '37', '37AAAFC1006A1Z6', 'RL-1006');

INSERT OR IGNORE INTO medicines (code, brand_name, generic_name, composition, manufacturer, category_id, dosage_form, strength, pack_size, purchase_price, selling_price, gst_rate, reorder_level, storage_condition) VALUES
    ('MED001', 'Calpol 500', 'Paracetamol', 'Paracetamol 500mg', 'GSK', (SELECT id FROM categories WHERE name = 'Analgesics'), 'Tablet', '500mg', 'Strip of 10', 12.00, 15.50, 12.00, 200, 'Below 25C'),
    ('MED002', 'Cetzine', 'Cetirizine', 'Cetirizine Hydrochloride 10mg', 'Dr Reddys Laboratories', (SELECT id FROM categories WHERE name = 'Antihistamines'), 'Tablet', '10mg', 'Strip of 10', 8.00, 11.00, 12.00, 150, 'Below 25C'),
    ('MED003', 'Limcee', 'Vitamin C', 'Ascorbic Acid 500mg', 'Abbott', (SELECT id FROM categories WHERE name = 'Vitamins'), 'Chewable Tablet', '500mg', 'Strip of 15', 18.00, 24.00, 12.00, 150, 'Below 25C, dry place'),
    ('MED004', 'Augmentin 625 Duo', 'Amoxicillin + Clavulanic Acid', 'Amoxicillin 500mg + Clavulanic Acid 125mg', 'GSK', (SELECT id FROM categories WHERE name = 'Antibiotics'), 'Tablet', '625mg', 'Strip of 6', 160.00, 201.00, 12.00, 80, 'Below 25C'),
    ('MED005', 'Azithral 500', 'Azithromycin', 'Azithromycin 500mg', 'Alembic', (SELECT id FROM categories WHERE name = 'Antibiotics'), 'Tablet', '500mg', 'Strip of 5', 95.00, 119.00, 12.00, 60, 'Below 30C'),
    ('MED006', 'Brufen 400', 'Ibuprofen', 'Ibuprofen 400mg', 'Abbott', (SELECT id FROM categories WHERE name = 'Analgesics'), 'Tablet', '400mg', 'Strip of 15', 22.00, 30.00, 12.00, 100, 'Below 25C'),
    ('MED007', 'Benadryl Cough Syrup', 'Diphenhydramine', 'Diphenhydramine 14.08mg/5ml', 'Johnson and Johnson', (SELECT id FROM categories WHERE name = 'Cough & Cold'), 'Syrup', '100ml', 'Bottle', 85.00, 112.00, 12.00, 80, 'Below 25C'),
    ('MED008', 'Pan 40', 'Pantoprazole', 'Pantoprazole 40mg', 'Alkem', (SELECT id FROM categories WHERE name = 'Gastrointestinal'), 'Tablet', '40mg', 'Strip of 15', 95.00, 155.00, 12.00, 100, 'Below 25C'),
    ('MED009', 'Glycomet 500', 'Metformin', 'Metformin Hydrochloride 500mg', 'USV', (SELECT id FROM categories WHERE name = 'Antidiabetic'), 'Tablet', '500mg', 'Strip of 20', 20.00, 28.00, 12.00, 150, 'Below 30C'),
    ('MED010', 'Human Mixtard 30/70', 'Human Insulin', 'Human Insulin 40IU/ml', 'Novo Nordisk', (SELECT id FROM categories WHERE name = 'Antidiabetic'), 'Injection', '40IU/ml', 'Vial 10ml', 120.00, 156.00, 5.00, 40, 'Refrigerate 2-8C'),
    ('MED011', 'Monocef 1g', 'Ceftriaxone', 'Ceftriaxone Sodium 1g', 'Aristo', (SELECT id FROM categories WHERE name = 'Antibiotics'), 'Injection', '1g', 'Vial', 38.00, 55.00, 12.00, 50, 'Below 25C'),
    ('MED012', 'Becosules', 'Vitamin B-Complex', 'B-Complex with Vitamin C', 'Pfizer', (SELECT id FROM categories WHERE name = 'Vitamins'), 'Capsule', 'Standard', 'Strip of 20', 32.00, 45.00, 12.00, 100, 'Below 25C'),
    ('MED013', 'Electral', 'Oral Rehydration Salts', 'WHO ORS formula', 'FDC', (SELECT id FROM categories WHERE name = 'Gastrointestinal'), 'Powder', '21.8g', 'Sachet', 18.00, 22.00, 12.00, 200, 'Dry place'),
    ('MED014', 'Surgical Gloves', 'Latex Examination Gloves', 'Sterile latex', 'Generic', (SELECT id FROM categories WHERE name = 'Medical Supplies'), 'Supply', 'Size 7', 'Pair', 12.00, 18.00, 12.00, 300, 'Dry place'),
    ('MED015', 'Amlokind 5', 'Amlodipine', 'Amlodipine Besylate 5mg', 'Mankind', (SELECT id FROM categories WHERE name = 'Cardiovascular'), 'Tablet', '5mg', 'Strip of 10', 14.00, 22.00, 12.00, 100, 'Below 30C');
