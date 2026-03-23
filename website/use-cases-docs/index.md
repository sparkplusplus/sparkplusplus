---
title: Use Case Examples
sidebar_position: 1
---

This section shows practical SparkPlusPlus jobs built around common data engineering patterns.

Current examples:

- [Customers + Orders to `customer_orders`](./customer-orders.md)
- [Customers + Payments to `customer_payment_summary`](./customer-payment-summary.md)
- [Orders + Order Items to `order_line_facts`](./order-line-facts.md)

Each example keeps the same framework shape:

- typed YAML config
- `SparkApp` entrypoint
- Spark session settings from `sparkConfig`
- application logic focused on joins and writes
