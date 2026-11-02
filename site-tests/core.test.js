// Tests for the browser demo's business rules (public/demo/core.js). Run: node --test site-tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const core = require('../public/demo/core.js');

const NOW = new Date(2026, 10, 2, 15, 30); // 2 Nov 2026, 15:30 local
const TODAY = core.todayIso(NOW);

function batch(id, expiryOffset, qty, extra) {
  return { id, medicineId: 1, batchNo: 'B' + id, mfg: core.addDays(TODAY, -300), expiry: core.addDays(TODAY, expiryOffset),
    qty, cost: 1000, mrp: 1800, status: 'ACTIVE', ...extra };
}

test('FEFO takes the earliest expiry first (specification example)', () => {
  const batches = [batch(102, 210, 150), batch(101, 60, 100), batch(103, 400, 200)];
  const a = core.allocateFefo(batches, 50, TODAY);
  assert.deepEqual(a.map(x => [x.batch.id, x.qty]), [[101, 50]]);
});

test('FEFO spills over to the next batch and skips expired, blocked and empty ones', () => {
  const batches = [batch(1, -1, 500), batch(2, 30, 40), batch(3, 10, 0), batch(4, 20, 99, { status: 'QUARANTINED' }),
    batch(5, 90, 100)];
  const a = core.allocateFefo(batches, 70, TODAY);
  assert.deepEqual(a.map(x => [x.batch.id, x.qty]), [[2, 40], [5, 30]]);
});

test('FEFO reports how much valid stock exists when short', () => {
  assert.throws(() => core.allocateFefo([batch(1, 30, 10), batch(2, -3, 50)], 11, TODAY, {}, 'Calpol'),
    e => e.insufficient && e.available === 10 && /Only 10 valid units of Calpol/.test(e.message));
});

test('invoice maths rounds half-up per line, like the desktop InvoiceCalculator', () => {
  // 7 x 15.50 = 108.50, 5% discount = 5.425 -> 5.43, taxable 103.07, 12% GST = 12.3684 -> 12.37
  assert.deepEqual(core.lineAmounts(7, 1550, 500, 1200),
    { gross: 10850, discount: 543, taxable: 10307, tax: 1237, total: 11544 });
  assert.throws(() => core.lineAmounts(0, 100, 0, 0), /Quantity/);
  assert.throws(() => core.lineAmounts(1, 100, 10001, 0), /Discount/);
});

test('GST: half-up CGST, remainder SGST; IGST across states; UTGST in Chandigarh', () => {
  assert.deepEqual(core.gstSplit(1805, 'INTRA_STATE', '36'), { cgst: 903, sgst: 902, igst: 0, stateTaxName: 'SGST' });
  assert.deepEqual(core.gstSplit(1805, 'INTER_STATE', '37'), { cgst: 0, sgst: 0, igst: 1805, stateTaxName: 'SGST' });
  assert.equal(core.gstSplit(100, 'INTRA_STATE', '04').stateTaxName, 'UTGST');
  assert.equal(core.supplyType('36', '36'), 'INTRA_STATE');
  assert.equal(core.supplyType('36', '27'), 'INTER_STATE');
});

test('money and percent parsing', () => {
  assert.equal(core.parseMoney('15.5', 'Rate'), 1550);
  assert.equal(core.parseMoney('1,200', 'Rate'), 120000);
  assert.equal(core.parseMoney('0.29', 'Rate'), 29);
  assert.throws(() => core.parseMoney('12.345', 'Rate'), /Rate/);
  assert.equal(core.parsePercent('', 'Disc'), 0);
  assert.equal(core.parsePercent('12.5', 'Disc'), 1250);
  assert.equal(core.formatMoney(12345678), 'Rs 1,23,456.78');
});

test('demo data matches the desktop seed: alerts present, sales present, stock never negative', () => {
  const s = core.createDemoState(NOW);
  assert.equal(s.medicines.length, 15);
  assert.ok(s.sales.length > 10);
  assert.ok(core.expiredInStock(s, TODAY).length >= 2);
  assert.ok(core.nearExpiry(s, TODAY).length >= 1);
  assert.ok(core.lowStock(s, TODAY).length >= 1);
  assert.ok(s.batches.every(b => b.qty >= 0));
  assert.ok(s.sales.some(x => x.supplyType === 'INTER_STATE'), 'an IGST sale to Andhra Pradesh');
  assert.ok(s.sales.some(x => x.status === 'PARTIAL'), 'a part-paid invoice');
  for (const sale of s.sales) {
    const paid = s.payments.filter(p => p.saleId === sale.id).reduce((t, p) => t + p.amount, 0);
    assert.equal(paid, sale.paid, 'payments add up for ' + sale.invoiceNo);
  }
  // Deterministic: every visitor starts from the same data.
  assert.equal(JSON.stringify(core.createDemoState(NOW)), JSON.stringify(s));
});

test('a sale deducts stock by FEFO, numbers the invoice and takes a partial payment', () => {
  const s = core.createDemoState(NOW);
  const calpol = s.medicines.find(m => m.code === 'MED001');
  const before = core.sellableStock(s, calpol.id, TODAY);
  const first = core.fefoOrder(core.batchesOf(s, calpol.id), TODAY)[0];
  const sale = core.createSale(s, { customerId: 6, lines: [{ medicineId: calpol.id, qty: 10 }],
    paymentStatus: 'PARTIAL', amountReceived: 10000, mode: 'UPI' }, NOW);
  assert.equal(core.sellableStock(s, calpol.id, TODAY), before - 10);
  assert.equal(sale.items[0].batchId, first.id);
  assert.match(sale.invoiceNo, /^INV-20261102-\d{4}$/);
  assert.equal(sale.supplyType, 'INTER_STATE');
  assert.equal(sale.total, 17360); // 10 x 15.50 = 155.00 + 12% = 173.60
  assert.equal(sale.status, 'PARTIAL');
  assert.throws(() => core.recordPayment(s, sale.id, 7361, 'CASH', null, TODAY), /exceeds the balance/);
  core.recordPayment(s, sale.id, 7360, 'CHEQUE', 'CHQ 1', TODAY);
  assert.equal(sale.status, 'PAID');
});

test('a failed line rolls back the whole order', () => {
  const s = core.createDemoState(NOW);
  const snapshot = JSON.stringify(s);
  const azithral = s.medicines.find(m => m.code === 'MED005');
  const calpol = s.medicines.find(m => m.code === 'MED001');
  assert.throws(() => core.createSale(s, { customerId: 1, lines: [{ medicineId: calpol.id, qty: 5 },
    { medicineId: azithral.id, qty: 9999 }], paymentStatus: 'PAID' }, NOW), e => e.insufficient);
  assert.equal(JSON.stringify(s), snapshot);
});

test('expired batches cannot be picked by hand', () => {
  const s = core.createDemoState(NOW);
  const expired = core.expiredInStock(s, TODAY)[0];
  assert.throws(() => core.createSale(s, { customerId: 1, lines: [{ medicineId: expired.medicineId, qty: 1,
    batchId: expired.id }], paymentStatus: 'PAID' }, NOW), /expired/);
});

test('receiving stock validates batch dates and tops up a matching batch', () => {
  const s = core.createDemoState(NOW);
  const med = s.medicines[0];
  const line = { medicineId: med.id, batchNo: 'NEW1', mfg: core.addDays(TODAY, -10), expiry: core.addDays(TODAY, 365), qty: 50 };
  const p = core.receiveStock(s, { supplierId: 2, lines: [line] }, NOW);
  assert.equal(p.supplyType, 'INTER_STATE');
  core.receiveStock(s, { supplierId: 1, lines: [{ ...line, batchNo: 'new1', qty: 25 }] }, NOW);
  assert.equal(s.batches.find(b => b.batchNo === 'NEW1').qty, 75);
  assert.throws(() => core.receiveStock(s, { supplierId: 1, lines: [{ ...line, batchNo: 'NEW2',
    expiry: core.addDays(TODAY, -1) }] }, NOW), /expired stock/);
  assert.throws(() => core.receiveStock(s, { supplierId: 1, lines: [{ ...line, batchNo: 'NEW3',
    mfg: core.addDays(TODAY, 2) }] }, NOW), /future/);
  assert.throws(() => core.receiveStock(s, { supplierId: 1, lines: [{ ...line, expiry: core.addDays(TODAY, 300) }] }, NOW),
    /different dates/);
});
