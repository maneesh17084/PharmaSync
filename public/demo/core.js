/*
 * PharmaSync browser demo: business rules, ported from the desktop app (com.pharmasync.service).
 * Pure functions over a plain state object; no DOM. Works in the browser (window.PSCore) and in Node (require).
 *
 * Conventions: money is integer paise, percentages are integer basis points (12% = 1200),
 * dates are 'YYYY-MM-DD' strings and timestamps 'YYYY-MM-DDTHH:MM' (local time).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.PSCore = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const DAY = 86400000;
  const COMPANY_STATE = '36';
  const NEAR_EXPIRY_DAYS = 90;

  /** A rule violation to show to the user (BusinessException in the desktop app). */
  class DemoError extends Error {
    constructor(message, extra) {
      super(message);
      this.name = 'DemoError';
      Object.assign(this, extra || {});
    }
  }

  function fail(message, extra) {
    throw new DemoError(message, extra);
  }

  // ---- dates ----

  function pad(n, w) {
    return String(n).padStart(w || 2, '0');
  }

  function todayIso(now) {
    const d = now || new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function nowStamp(now) {
    const d = now || new Date();
    return todayIso(d) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function toUtc(iso) {
    const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  }

  function addDays(iso, n) {
    const t = new Date(toUtc(iso) + n * DAY);
    return t.getUTCFullYear() + '-' + pad(t.getUTCMonth() + 1) + '-' + pad(t.getUTCDate());
  }

  /** Whole days from a to b (b - a). */
  function daysBetween(a, b) {
    return Math.round((toUtc(b) - toUtc(a)) / DAY);
  }

  // ---- money ----

  /** n / d rounded half-up, for non-negative integers. */
  function roundDiv(n, d) {
    return Math.floor((2 * n + d) / (2 * d));
  }

  /** "15.50" -> 1550 paise. */
  function parseMoney(text, field) {
    const t = String(text == null ? '' : text).replace(/,/g, '').trim();
    if (!/^\d+(\.\d{0,2})?$/.test(t)) {
      fail(field + ' must be an amount like 120 or 120.50');
    }
    const [whole, frac = ''] = t.split('.');
    return Number(whole) * 100 + Number((frac + '00').slice(0, 2));
  }

  /** "5" or "12.5" -> basis points. */
  function parsePercent(text, field) {
    const t = String(text == null ? '' : text).trim() || '0';
    if (!/^\d+(\.\d{0,2})?$/.test(t)) {
      fail(field + ' must be a number');
    }
    const [whole, frac = ''] = t.split('.');
    return Number(whole) * 100 + Number((frac + '00').slice(0, 2));
  }

  /**
   * gross = qty x price, discount on gross, GST on (gross - discount), each rounded half-up to the paisa
   * (InvoiceCalculator.line).
   */
  function lineAmounts(qty, price, discBp, gstBp) {
    if (!Number.isInteger(qty) || qty <= 0) {
      fail('Quantity must be greater than zero');
    }
    if (price < 0) {
      fail('Rate must not be negative');
    }
    if (discBp < 0 || discBp > 10000) {
      fail('Discount must be between 0 and 100 percent');
    }
    const gross = qty * price;
    const discount = roundDiv(gross * discBp, 10000);
    const taxable = gross - discount;
    const tax = roundDiv(taxable * gstBp, 10000);
    return { gross, discount, taxable, tax, total: taxable + tax };
  }

  function totals(lines) {
    const t = { subtotal: 0, discount: 0, tax: 0, total: 0 };
    for (const l of lines) {
      t.subtotal += l.gross;
      t.discount += l.discount;
      t.tax += l.tax;
    }
    t.total = t.subtotal - t.discount + t.tax;
    return t;
  }

  // ---- GST ----

  const STATES = {
    '01': 'Jammu and Kashmir', '02': 'Himachal Pradesh', '03': 'Punjab', '04': 'Chandigarh', '05': 'Uttarakhand',
    '06': 'Haryana', '07': 'Delhi', '08': 'Rajasthan', '09': 'Uttar Pradesh', '10': 'Bihar', '11': 'Sikkim',
    '12': 'Arunachal Pradesh', '13': 'Nagaland', '14': 'Manipur', '15': 'Mizoram', '16': 'Tripura', '17': 'Meghalaya',
    '18': 'Assam', '19': 'West Bengal', '20': 'Jharkhand', '21': 'Odisha', '22': 'Chhattisgarh', '23': 'Madhya Pradesh',
    '24': 'Gujarat', '26': 'Dadra and Nagar Haveli and Daman and Diu', '27': 'Maharashtra', '29': 'Karnataka',
    '30': 'Goa', '31': 'Lakshadweep', '32': 'Kerala', '33': 'Tamil Nadu', '34': 'Puducherry',
    '35': 'Andaman and Nicobar Islands', '36': 'Telangana', '37': 'Andhra Pradesh', '38': 'Ladakh'
  };
  /** Union territories without a legislature charge UTGST instead of SGST. */
  const UTGST = new Set(['04', '26', '31', '35', '38']);

  function stateLabel(code) {
    return code && STATES[code] ? code + ' - ' + STATES[code] : '-';
  }

  function supplyType(supplierState, placeOfSupply) {
    return supplierState === placeOfSupply ? 'INTRA_STATE' : 'INTER_STATE';
  }

  /** CGST gets half the tax rounded half-up, SGST/UTGST the rest; inter-state is all IGST. */
  function gstSplit(tax, type, placeOfSupply) {
    if (type === 'INTER_STATE') {
      return { cgst: 0, sgst: 0, igst: tax, stateTaxName: 'SGST' };
    }
    const cgst = roundDiv(tax, 2);
    return { cgst, sgst: tax - cgst, igst: 0, stateTaxName: UTGST.has(placeOfSupply) ? 'UTGST' : 'SGST' };
  }

  // ---- expiry and stock ----

  function expiryStatus(batch, today, threshold) {
    if (batch.expiry < today) {
      return 'EXPIRED';
    }
    return batch.expiry <= addDays(today, threshold == null ? NEAR_EXPIRY_DAYS : threshold) ? 'NEAR_EXPIRY' : 'VALID';
  }

  function isSellable(batch, today) {
    return batch.status === 'ACTIVE' && batch.qty > 0 && batch.expiry >= today;
  }

  /** Sellable batches in dispatch order: earliest expiry first (FEFO), then oldest batch. */
  function fefoOrder(batches, today) {
    return batches.filter(b => isSellable(b, today))
      .sort((a, b) => (a.expiry < b.expiry ? -1 : a.expiry > b.expiry ? 1 : a.id - b.id));
  }

  function batchesOf(state, medicineId) {
    return state.batches.filter(b => b.medicineId === medicineId);
  }

  function sellableStock(state, medicineId, today) {
    return fefoOrder(batchesOf(state, medicineId), today).reduce((s, b) => s + b.qty, 0);
  }

  /**
   * Takes qty from batches in FEFO order, spilling over to the next batch. used maps batch id -> units already
   * planned by earlier lines of the same order. Throws when valid stock is short (error.available is set).
   */
  function allocateFefo(batches, qty, today, used, medicineName) {
    if (!Number.isInteger(qty) || qty <= 0) {
      fail('Quantity must be greater than zero');
    }
    const taken = used || {};
    const out = [];
    let need = qty;
    let available = 0;
    for (const b of fefoOrder(batches, today)) {
      const free = b.qty - (taken[b.id] || 0);
      if (free <= 0) {
        continue;
      }
      available += free;
      if (need > 0) {
        const take = Math.min(free, need);
        out.push({ batch: b, qty: take });
        need -= take;
      }
    }
    if (need > 0) {
      fail('Only ' + available + ' valid units of ' + (medicineName || 'this medicine') + ' are available (requested '
        + qty + ')', { available, insufficient: true });
    }
    return out;
  }

  function lowStock(state, today) {
    return state.medicines.filter(m => m.active)
      .map(m => ({ medicine: m, stock: sellableStock(state, m.id, today) }))
      .filter(x => x.stock < x.medicine.reorder)
      .map(x => ({ ...x, shortfall: x.medicine.reorder - x.stock,
        suggested: Math.ceil(Math.max(x.medicine.reorder * 2 - x.stock, 0) / 10) * 10 }))
      .sort((a, b) => (a.stock - a.medicine.reorder) - (b.stock - b.medicine.reorder));
  }

  function nearExpiry(state, today) {
    const until = addDays(today, NEAR_EXPIRY_DAYS);
    return state.batches.filter(b => b.status === 'ACTIVE' && b.qty > 0 && b.expiry >= today && b.expiry <= until)
      .sort((a, b) => (a.expiry < b.expiry ? -1 : 1));
  }

  function expiredInStock(state, today) {
    return state.batches.filter(b => (b.status === 'ACTIVE' || b.status === 'QUARANTINED') && b.qty > 0
      && b.expiry < today).sort((a, b) => (a.expiry < b.expiry ? -1 : 1));
  }

  // ---- records ----

  function byId(list, id) {
    return list.find(x => x.id === Number(id));
  }

  function nextId(state, kind) {
    state.seq[kind] = (state.seq[kind] || 0) + 1;
    return state.seq[kind];
  }

  /** INV-20261102-0001 style numbers, sequential per day. */
  function nextNumber(prefix, date, existing) {
    const p = prefix + '-' + date.replace(/-/g, '') + '-';
    const used = existing.filter(n => n.startsWith(p)).map(n => Number(n.slice(p.length)));
    return p + pad((used.length ? Math.max(...used) : 0) + 1, 4);
  }

  function statusFor(paid, total) {
    if (paid === 0) {
      return 'UNPAID';
    }
    return paid >= total ? 'PAID' : 'PARTIAL';
  }

  function move(state, type, batch, qty, ref, when) {
    state.movements.push({ id: nextId(state, 'movement'), when, type, batchId: batch.id,
      medicineId: batch.medicineId, qty, balance: batch.qty, ref });
  }

  // ---- sales ----

  /**
   * Confirms an order: FEFO allocation (or a chosen batch), invoice with GST by place of supply, stock deduction
   * and any payment taken at billing. Everything is validated before anything changes (like the desktop
   * app's single transaction).
   * req: { customerId, lines: [{ medicineId, qty, batchId?, price?, discBp? }], paymentStatus, amountReceived?,
   *        mode, when? }
   */
  function createSale(state, req, now) {
    const today = todayIso(now);
    const when = req.when || nowStamp(now);
    const customer = byId(state.customers, req.customerId);
    if (!customer) {
      fail('Select a customer');
    }
    if (!req.lines || req.lines.length === 0) {
      fail('Add at least one medicine to the order');
    }
    const used = {};
    const planned = [];
    for (const line of req.lines) {
      const med = byId(state.medicines, line.medicineId);
      if (!med) {
        fail('Medicine not found');
      }
      if (!med.active) {
        fail(med.brand + ' is inactive and cannot be sold');
      }
      const price = line.price == null ? med.price : line.price;
      const discBp = line.discBp || 0;
      let allocations;
      if (line.batchId) {
        const chosen = byId(state.batches, line.batchId);
        if (!chosen || chosen.medicineId !== med.id) {
          fail('Selected batch does not belong to ' + med.brand);
        }
        if (chosen.expiry < today) {
          fail('Batch ' + chosen.batchNo + ' expired on ' + chosen.expiry + ' and cannot be dispatched');
        }
        if (!isSellable(chosen, today)) {
          fail('Batch ' + chosen.batchNo + ' is ' + chosen.status + ' and cannot be dispatched');
        }
        const free = chosen.qty - (used[chosen.id] || 0);
        if (free < line.qty) {
          fail('Only ' + free + ' units left in batch ' + chosen.batchNo, { available: free, insufficient: true });
        }
        allocations = [{ batch: chosen, qty: line.qty }];
      } else {
        allocations = allocateFefo(batchesOf(state, med.id), line.qty, today, used, med.brand);
      }
      for (const a of allocations) {
        used[a.batch.id] = (used[a.batch.id] || 0) + a.qty;
        planned.push({ med, batch: a.batch, qty: a.qty, price, discBp,
          amounts: lineAmounts(a.qty, price, discBp, med.gstBp) });
      }
    }
    const t = totals(planned.map(p => p.amounts));
    const status = req.paymentStatus || 'UNPAID';
    let received = 0;
    if (status === 'PAID') {
      received = t.total;
    } else if (status === 'PARTIAL') {
      received = req.amountReceived;
      if (!(received > 0) || received >= t.total) {
        fail('For a partial payment enter the amount received: more than 0 and less than the invoice total of '
          + formatMoney(t.total));
      }
    }
    const type = supplyType(state.company.state, customer.state);
    const sale = {
      id: nextId(state, 'sale'),
      invoiceNo: nextNumber('INV', when.slice(0, 10), state.sales.map(s => s.invoiceNo)),
      customerId: customer.id, when, placeOfSupply: customer.state, supplyType: type,
      mode: req.mode || 'CREDIT', paid: 0, status: 'UNPAID',
      subtotal: t.subtotal, discount: t.discount, tax: t.tax, total: t.total,
      items: planned.map(p => ({ medicineId: p.med.id, batchId: p.batch.id, batchNo: p.batch.batchNo,
        expiry: p.batch.expiry, qty: p.qty, price: p.price, discBp: p.discBp, gstBp: p.med.gstBp,
        tax: p.amounts.tax, total: p.amounts.total }))
    };
    for (const p of planned) {
      p.batch.qty -= p.qty;
      move(state, 'SALE_OUT', p.batch, -p.qty, sale.invoiceNo, when);
    }
    state.sales.push(sale);
    if (received > 0) {
      addPayment(state, sale, received, req.mode, 'At billing', when.slice(0, 10));
    }
    return sale;
  }

  function addPayment(state, sale, amount, mode, reference, date) {
    if (!(amount > 0)) {
      fail('Payment amount must be greater than zero');
    }
    if (amount > sale.total - sale.paid) {
      fail('Payment of ' + formatMoney(amount) + ' exceeds the balance due of ' + formatMoney(sale.total - sale.paid));
    }
    if (date < sale.when.slice(0, 10)) {
      fail('Payment date cannot be before the invoice date ' + sale.when.slice(0, 10));
    }
    const m = !mode || mode === 'CREDIT' ? 'CASH' : mode;
    state.payments.push({ id: nextId(state, 'payment'), saleId: sale.id, date, amount, mode: m,
      reference: reference || null });
    sale.paid += amount;
    sale.status = statusFor(sale.paid, sale.total);
  }

  function recordPayment(state, saleId, amount, mode, reference, date, now) {
    const sale = byId(state.sales, saleId);
    if (!sale) {
      fail('Invoice not found');
    }
    addPayment(state, sale, amount, mode, reference, date || todayIso(now));
    return sale;
  }

  // ---- purchases ----

  /** PurchaseService.validateBatchInfo */
  function validateBatchInfo(batchNo, mfg, expiry, receivedOn, medicine) {
    if (!batchNo || !String(batchNo).trim()) {
      fail('Batch number is required for ' + medicine);
    }
    if (!mfg || !expiry) {
      fail('Manufacturing and expiry dates are required for ' + medicine);
    }
    if (expiry <= mfg) {
      fail('Expiry date must be after manufacturing date for ' + medicine);
    }
    if (mfg > receivedOn) {
      fail('Manufacturing date cannot be in the future for ' + medicine);
    }
    if (expiry < receivedOn) {
      fail('Cannot receive expired stock: ' + medicine + ' batch ' + batchNo);
    }
  }

  /**
   * Records a received shipment: each line creates a batch, or tops up the same batch number when its dates match.
   * req: { supplierId, invoiceRef, lines: [{ medicineId, batchNo, mfg, expiry, qty, cost, mrp }] }
   */
  function receiveStock(state, req, now) {
    const today = todayIso(now);
    const supplier = byId(state.suppliers, req.supplierId);
    if (!supplier) {
      fail('Select a supplier');
    }
    if (!req.lines || req.lines.length === 0) {
      fail('Add at least one medicine');
    }
    const plan = req.lines.map(l => {
      const med = byId(state.medicines, l.medicineId);
      if (!med) {
        fail('Select a medicine');
      }
      if (!Number.isInteger(l.qty) || l.qty <= 0) {
        fail('Quantity for ' + med.brand + ' must be greater than zero');
      }
      const batchNo = String(l.batchNo || '').trim().toUpperCase();
      validateBatchInfo(batchNo, l.mfg, l.expiry, today, med.brand);
      const existing = state.batches.find(b => b.medicineId === med.id && b.batchNo === batchNo);
      if (existing && (existing.mfg !== l.mfg || existing.expiry !== l.expiry)) {
        fail('Batch ' + batchNo + ' of ' + med.brand + ' already exists with different dates (mfg ' + existing.mfg
          + ', exp ' + existing.expiry + ')');
      }
      if (existing && existing.status !== 'ACTIVE') {
        fail('Batch ' + batchNo + ' is ' + existing.status + ' and cannot receive stock');
      }
      const cost = l.cost == null ? med.cost : l.cost;
      return { med, batchNo, existing, line: l, cost, amounts: lineAmounts(l.qty, cost, 0, med.gstBp) };
    });
    const t = totals(plan.map(p => p.amounts));
    const purchase = {
      id: nextId(state, 'purchase'),
      poNo: nextNumber('PO', today, state.purchases.map(p => p.poNo)),
      supplierId: supplier.id, invoiceRef: req.invoiceRef || null, date: today,
      supplyType: supplyType(supplier.state, state.company.state),
      subtotal: t.subtotal, tax: t.tax, total: t.total, items: []
    };
    for (const p of plan) {
      let batch = p.existing;
      if (batch) {
        batch.qty += p.line.qty;
      } else {
        batch = { id: nextId(state, 'batch'), medicineId: p.med.id, batchNo: p.batchNo, mfg: p.line.mfg,
          expiry: p.line.expiry, qty: p.line.qty, cost: p.cost,
          mrp: p.line.mrp == null ? Math.round(p.med.price * 1.15) : p.line.mrp, supplierId: supplier.id, status: 'ACTIVE' };
        state.batches.push(batch);
      }
      move(state, 'PURCHASE_IN', batch, p.line.qty, purchase.poNo, nowStamp(now));
      purchase.items.push({ medicineId: p.med.id, batchId: batch.id, batchNo: p.batchNo, mfg: p.line.mfg,
        expiry: p.line.expiry, qty: p.line.qty, cost: p.cost, total: p.amounts.total });
    }
    state.purchases.push(purchase);
    return purchase;
  }

  // ---- dashboard & reports ----

  function dashboard(state, now) {
    const today = todayIso(now);
    const month = today.slice(0, 8) + '01';
    const todays = state.sales.filter(s => s.when.slice(0, 10) === today);
    const sum = (list, f) => list.reduce((s, x) => s + f(x), 0);
    return {
      todaySales: sum(todays, s => s.total),
      todayInvoices: todays.length,
      monthSales: sum(state.sales.filter(s => s.when.slice(0, 10) >= month), s => s.total),
      receivables: sum(state.sales, s => s.total - s.paid),
      stockValue: sum(state.batches.filter(b => isSellable(b, today)), b => b.qty * b.cost),
      activeMedicines: state.medicines.filter(m => m.active).length,
      lowStock: lowStock(state, today).length,
      nearExpiry: nearExpiry(state, today).length,
      expired: expiredInStock(state, today).length
    };
  }

  function dailySales(state, days, now) {
    const today = todayIso(now);
    const out = [];
    for (let i = days - 1; i >= 0; i--) {
      const day = addDays(today, -i);
      out.push({ day, total: state.sales.filter(s => s.when.slice(0, 10) === day).reduce((t, s) => t + s.total, 0) });
    }
    return out;
  }

  function topMedicines(state, days, limit, now) {
    const from = addDays(todayIso(now), -(days - 1));
    const qty = {};
    for (const s of state.sales) {
      if (s.when.slice(0, 10) >= from) {
        for (const i of s.items) {
          qty[i.medicineId] = (qty[i.medicineId] || 0) + i.qty;
        }
      }
    }
    return Object.entries(qty).map(([id, q]) => ({ medicine: byId(state.medicines, id), qty: q }))
      .sort((a, b) => b.qty - a.qty).slice(0, limit);
  }

  function formatMoney(paise) {
    const neg = paise < 0;
    const v = Math.abs(paise);
    const rupees = Math.floor(v / 100).toLocaleString('en-IN');
    return (neg ? '-' : '') + 'Rs ' + rupees + '.' + pad(v % 100);
  }

  // ---- demo data (same as the desktop app's seed.sql and DemoDataSeeder) ----

  /** Small deterministic random generator so every visitor starts from the same demo data. */
  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a = (a + 0x6D2B79F5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const MEDICINES = [
    ['MED001', 'Calpol 500', 'Paracetamol', 'Analgesics', 'Tablet', '500mg', 'Strip of 10', '12.00', '15.50', 12, 200],
    ['MED002', 'Cetzine', 'Cetirizine', 'Antihistamines', 'Tablet', '10mg', 'Strip of 10', '8.00', '11.00', 12, 150],
    ['MED003', 'Limcee', 'Vitamin C', 'Vitamins', 'Chewable Tablet', '500mg', 'Strip of 15', '18.00', '24.00', 12, 150],
    ['MED004', 'Augmentin 625 Duo', 'Amoxicillin + Clavulanic Acid', 'Antibiotics', 'Tablet', '625mg', 'Strip of 6', '160.00', '201.00', 12, 80],
    ['MED005', 'Azithral 500', 'Azithromycin', 'Antibiotics', 'Tablet', '500mg', 'Strip of 5', '95.00', '119.00', 12, 60],
    ['MED006', 'Brufen 400', 'Ibuprofen', 'Analgesics', 'Tablet', '400mg', 'Strip of 15', '22.00', '30.00', 12, 100],
    ['MED007', 'Benadryl Cough Syrup', 'Diphenhydramine', 'Cough & Cold', 'Syrup', '100ml', 'Bottle', '85.00', '112.00', 12, 80],
    ['MED008', 'Pan 40', 'Pantoprazole', 'Gastrointestinal', 'Tablet', '40mg', 'Strip of 15', '95.00', '155.00', 12, 100],
    ['MED009', 'Glycomet 500', 'Metformin', 'Antidiabetic', 'Tablet', '500mg', 'Strip of 20', '20.00', '28.00', 12, 150],
    ['MED010', 'Human Mixtard 30/70', 'Human Insulin', 'Antidiabetic', 'Injection', '40IU/ml', 'Vial 10ml', '120.00', '156.00', 5, 40],
    ['MED011', 'Monocef 1g', 'Ceftriaxone', 'Antibiotics', 'Injection', '1g', 'Vial', '38.00', '55.00', 12, 50],
    ['MED012', 'Becosules', 'Vitamin B-Complex', 'Vitamins', 'Capsule', 'Standard', 'Strip of 20', '32.00', '45.00', 12, 100],
    ['MED013', 'Electral', 'Oral Rehydration Salts', 'Gastrointestinal', 'Powder', '21.8g', 'Sachet', '18.00', '22.00', 12, 200],
    ['MED014', 'Surgical Gloves', 'Latex Examination Gloves', 'Medical Supplies', 'Supply', 'Size 7', 'Pair', '12.00', '18.00', 12, 300],
    ['MED015', 'Amlokind 5', 'Amlodipine', 'Cardiovascular', 'Tablet', '5mg', 'Strip of 10', '14.00', '22.00', 12, 100]
  ];

  // code, batch, mfg offset, expiry offset (days from today), qty, MRP
  const OPENING = [
    ['MED001', 'B101', -300, 60, 100, '18.00'], ['MED001', 'B102', -200, 210, 150, '18.00'],
    ['MED001', 'B103', -60, 400, 200, '18.00'], ['MED002', 'CTZ2401', -240, 330, 260, '13.50'],
    ['MED003', 'LMC2311', -500, 75, 80, '28.00'], ['MED003', 'LMC2409', -90, 500, 300, '28.00'],
    ['MED004', 'AUG2402', -200, 380, 140, '228.00'], ['MED005', 'AZ0921', -720, -20, 40, '132.00'],
    ['MED005', 'AZ1123', -150, 300, 30, '132.00'], ['MED006', 'BRF2405', -120, 560, 220, '36.00'],
    ['MED007', 'BEN2402', -330, 50, 60, '128.00'], ['MED007', 'BEN2410', -30, 700, 120, '128.00'],
    ['MED008', 'PAN2406', -100, 620, 240, '178.00'], ['MED009', 'GLY2403', -210, 450, 400, '32.00'],
    ['MED010', 'HMX2408', -60, 120, 30, '170.00'], ['MED011', 'MNC2301', -700, -5, 25, '65.00'],
    ['MED011', 'MNC2404', -300, 45, 20, '65.00'], ['MED012', 'BEC2407', -80, 640, 180, '52.00'],
    ['MED013', 'ORS2405', -150, 580, 500, '25.00'], ['MED014', 'GLV2401', -280, 1000, 900, '22.00'],
    ['MED015', 'AML2404', -180, 15, 50, '26.00'], ['MED015', 'AML2408', -40, 690, 160, '26.00']
  ];

  // supplier id, invoice ref, received offset, [code, qty, batch, mfg offset, expiry offset]
  const PURCHASES = [
    [1, 'APX/INV/7781', -24, [['MED001', 300, 'B104', -40, 720], ['MED006', 150, 'BRF2410', -30, 700]]],
    [2, 'SUN/2231', -11, [['MED002', 200, 'CTZ2410', -20, 690], ['MED008', 100, 'PAN2410', -25, 700],
      ['MED013', 300, 'ORS2410', -18, 710]]],
    [3, 'NMS-5512', -2, [['MED004', 60, 'AUG2410', -15, 680], ['MED012', 120, 'BEC2410', -10, 700]]]
  ];

  /** Medicines kept out of demo sales so their low-stock and expiry alerts stay visible. */
  const NOT_SOLD = new Set(['MED005', 'MED010', 'MED011']);
  const FAST = new Set(['MED001', 'MED002', 'MED013']);

  function createDemoState(now) {
    const today = todayIso(now);
    const state = {
      version: 1,
      createdOn: today,
      company: { name: 'PharmaSync Demo Pharma (SAMPLE COMPANY)', address: 'Demo address, Hyderabad, Telangana',
        state: COMPANY_STATE },
      seq: {},
      medicines: [], batches: [], sales: [], payments: [], purchases: [], movements: [],
      suppliers: [
        { id: 1, name: 'Apex Pharma Distributors', city: 'Secunderabad, Telangana', state: '36', gstin: '36AAACA1111A1Z1' },
        { id: 2, name: 'Sunrise Healthcare Agencies', city: 'Vijayawada, Andhra Pradesh', state: '37', gstin: '37AAACS2222B1Z2' },
        { id: 3, name: 'National Medico Supplies', city: 'Mumbai, Maharashtra', state: '27', gstin: '27AAACN3333C1Z3' }
      ],
      customers: [
        { id: 1, name: 'Care Plus Pharmacy', type: 'PHARMACY', city: 'Kukatpally, Hyderabad', state: '36', gstin: '36AAAFC1001A1Z1', phone: '+91 90000 10001' },
        { id: 2, name: 'City General Hospital', type: 'HOSPITAL', city: 'Banjara Hills, Hyderabad', state: '36', gstin: '36AAAFC1002A1Z2', phone: '+91 90000 10002' },
        { id: 3, name: 'Wellness Medicals', type: 'PHARMACY', city: 'Ameerpet, Hyderabad', state: '36', gstin: '36AAAFW1003A1Z3', phone: '+91 90000 10003' },
        { id: 4, name: 'Sri Sai Clinic', type: 'CLINIC', city: 'Madhapur, Hyderabad', state: '36', gstin: null, phone: '+91 90000 10004' },
        { id: 5, name: 'Lifeline Multispeciality Hospital', type: 'HOSPITAL', city: 'Gachibowli, Hyderabad', state: '36', gstin: '36AAAFL1005A1Z5', phone: '+91 90000 10005' },
        { id: 6, name: 'Coastal Pharma Traders', type: 'PHARMACY', city: 'Dwaraka Nagar, Visakhapatnam', state: '37', gstin: '37AAAFC1006A1Z6', phone: '+91 90000 10006' }
      ]
    };
    state.seq.supplier = 3;
    state.seq.customer = 6;
    const byCode = {};
    for (const m of MEDICINES) {
      const med = { id: nextId(state, 'medicine'), code: m[0], brand: m[1], generic: m[2], category: m[3], form: m[4],
        strength: m[5], pack: m[6], cost: parseMoney(m[7]), price: parseMoney(m[8]), gstBp: m[9] * 100,
        reorder: m[10], active: true };
      state.medicines.push(med);
      byCode[med.code] = med;
    }
    for (const o of OPENING) {
      const med = byCode[o[0]];
      const batch = { id: nextId(state, 'batch'), medicineId: med.id, batchNo: o[1], mfg: addDays(today, o[2]),
        expiry: addDays(today, o[3]), qty: o[4], cost: med.cost, mrp: parseMoney(o[5]), supplierId: 1, status: 'ACTIVE' };
      state.batches.push(batch);
      move(state, 'OPENING', batch, o[4], 'OPENING', addDays(today, -30) + 'T09:00');
    }
    for (const [supplierId, ref, offset, lines] of PURCHASES) {
      const received = addDays(today, offset);
      const fakeNow = new Date(toUtc(received) + new Date().getTimezoneOffset() * 60000 + 11 * 3600000);
      receiveStock(state, { supplierId, invoiceRef: ref, lines: lines.map(([code, qty, batchNo, mfg, exp]) => ({
        medicineId: byCode[code].id, qty, batchNo, mfg: addDays(today, mfg), expiry: addDays(today, exp),
        mrp: Math.round(byCode[code].price * 1.15) })) }, fakeNow);
    }

    const rnd = mulberry32(42);
    const int = n => Math.floor(rnd() * n);
    const sellable = state.medicines.filter(m => !NOT_SOLD.has(m.code));
    const nowStampValue = nowStamp(now);
    let creditInvoices = 0;
    for (let daysAgo = 29; daysAgo >= 0; daysAgo--) {
      const day = addDays(today, -daysAgo);
      // Up to two invoices a day; always two today so the dashboard's "Today's sales" has something to show.
      const count = daysAgo === 0 ? 2 : int(3);
      for (let k = 0; k < count; k++) {
        let when = day + 'T' + pad(9 + int(8)) + ':' + pad(int(60));
        if (when > nowStampValue) {
          when = nowStampValue;
        }
        const usedIdx = new Set();
        const lines = [];
        const lineCount = 1 + int(3);
        for (let l = 0; l < lineCount; l++) {
          const idx = int(sellable.length);
          if (usedIdx.has(idx)) {
            continue;
          }
          usedIdx.add(idx);
          const med = sellable[idx];
          const base = FAST.has(med.code) ? 30 : 8;
          lines.push({ medicineId: med.id, qty: base + int(base), discBp: int(4) === 0 ? 500 : 0 });
        }
        const customer = state.customers[int(state.customers.length)];
        const paid = daysAgo > 7 || rnd() < 0.5;
        try {
          const sale = createSale(state, { customerId: customer.id, lines, paymentStatus: paid ? 'PAID' : 'UNPAID',
            mode: paid ? 'NEFT' : 'CREDIT', when }, now);
          // Every other older credit invoice gets a part-payment a day later, to show the payment ledger.
          if (!paid && daysAgo >= 2 && creditInvoices++ % 2 === 0) {
            addPayment(state, sale, Math.floor(sale.total / 200) * 100, 'UPI', 'Part payment', addDays(day, 1));
          }
        } catch (e) {
          if (!(e instanceof DemoError)) {
            throw e;
          }
          // not enough stock for this random order; skip it, as the desktop seeder does
        }
      }
    }
    return state;
  }

  return {
    DemoError, NEAR_EXPIRY_DAYS, STATES, stateLabel,
    todayIso, nowStamp, addDays, daysBetween,
    roundDiv, parseMoney, parsePercent, formatMoney, lineAmounts, totals,
    supplyType, gstSplit, expiryStatus, isSellable, fefoOrder, batchesOf, sellableStock, allocateFefo,
    lowStock, nearExpiry, expiredInStock, statusFor, nextNumber, byId,
    createSale, recordPayment, receiveStock, validateBatchInfo,
    dashboard, dailySales, topMedicines, createDemoState
  };
});
