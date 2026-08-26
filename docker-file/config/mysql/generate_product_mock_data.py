#!/usr/bin/env python3
"""Generate a deterministic, relationally consistent product_db data set.

The script intentionally uses the MySQL client inside the development Docker container,
so the repository does not need a Python database-driver dependency. It is safe to rerun:
each invocation only tops tables up to the requested target sizes, and every order plus
its items is committed in one transaction.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from datetime import date, timedelta
from decimal import Decimal


CATEGORY_NAMES = (
	"电子产品",
	"服装鞋帽",
	"图书音像",
	"家居家装",
	"食品饮料",
	"美妆个护",
	"母婴用品",
	"运动户外",
	"汽车用品",
	"办公文具",
	"数码配件",
	"家用电器",
	"珠宝配饰",
	"玩具乐器",
	"宠物用品",
	"生鲜果蔬",
	"医药保健",
	"旅行用品",
	"厨房用品",
	"清洁用品",
	"园艺工具",
	"智能设备",
	"摄影器材",
	"电脑整机",
	"网络设备",
	"床上用品",
	"箱包皮具",
	"钟表眼镜",
	"休闲零食",
	"礼品鲜花",
)

PRODUCT_PREFIXES = (
	"经典",
	"轻奢",
	"智能",
	"便携",
	"专业",
	"环保",
	"家庭",
	"商务",
	"青春",
	"旗舰",
)

PRODUCT_TYPES = (
	"手机",
	"耳机",
	"键盘",
	"显示器",
	"外套",
	"运动鞋",
	"双肩包",
	"咖啡机",
	"料理锅",
	"台灯",
	"书籍",
	"牛奶",
	"巧克力",
	"护肤套装",
	"儿童玩具",
	"健身器材",
	"办公套装",
	"宠物食品",
	"床品套装",
	"旅行箱",
)


def parse_args() -> argparse.Namespace:
	parser = argparse.ArgumentParser(description=__doc__)
	parser.add_argument("--container", default=os.getenv("PRODUCT_DB_CONTAINER", "data-agent-dev-mysql-1"))
	parser.add_argument("--database", default=os.getenv("PRODUCT_DB_NAME", "product_db"))
	parser.add_argument("--user", default=os.getenv("PRODUCT_DB_USER", "root"))
	parser.add_argument("--password", default=os.getenv("PRODUCT_DB_PASSWORD", "root"))
	parser.add_argument("--users", type=int, default=10_000, help="Target user count")
	parser.add_argument("--orders", type=int, default=1_000_000, help="Target order count")
	parser.add_argument("--products", type=int, default=500, help="Target product count")
	parser.add_argument("--categories", type=int, default=30, help="Target category count")
	parser.add_argument("--batch-size", type=int, default=2_000, help="Orders per transaction")
	return parser.parse_args()


class DockerMysql:
	def __init__(self, args: argparse.Namespace) -> None:
		self.command = [
			"docker",
			"exec",
			"-i",
			args.container,
			"mysql",
			f"-u{args.user}",
			f"-p{args.password}",
			"--default-character-set=utf8mb4",
			"--batch",
			"--skip-column-names",
			args.database,
		]

	def query(self, sql: str) -> list[list[str]]:
		result = subprocess.run(
			self.command + ["--execute", sql],
			check=True,
			text=True,
			capture_output=True,
		)
		return [line.split("\t") for line in result.stdout.splitlines() if line]

	def execute(self, sql: str) -> None:
		subprocess.run(self.command, input=sql, check=True, text=True)

	def open_stream(self) -> subprocess.Popen[str]:
		return subprocess.Popen(self.command, stdin=subprocess.PIPE, text=True)


def scalar(mysql: DockerMysql, sql: str) -> int:
	rows = mysql.query(sql)
	return int(rows[0][0]) if rows else 0


def sql_string(value: str) -> str:
	return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def chunks(values: list[str], size: int) -> list[list[str]]:
	return [values[index : index + size] for index in range(0, len(values), size)]


def insert_values(mysql: DockerMysql, prefix: str, values: list[str], batch_size: int = 2_000) -> None:
	for batch in chunks(values, batch_size):
		mysql.execute(prefix + ",\n".join(batch) + ";\n")


def top_up_dimensions(mysql: DockerMysql, args: argparse.Namespace) -> None:
	user_count = scalar(mysql, "SELECT COUNT(*) FROM users")
	if user_count < args.users:
		start_id = scalar(mysql, "SELECT COALESCE(MAX(id), 0) + 1 FROM users")
		values = []
		for offset in range(args.users - user_count):
			user_id = start_id + offset
			created = date(2021, 1, 1) + timedelta(days=(user_id * 17) % 1_825)
			values.append(
				f"({user_id}, 'user_{user_id:08d}', 'user_{user_id:08d}@example.com', "
				f"'{created.isoformat()} {(user_id * 97) % 24:02d}:{(user_id * 31) % 60:02d}:00')"
			)
		insert_values(mysql, "INSERT INTO users (id, username, email, created_at) VALUES\n", values)
		print(f"users: added {len(values):,}", flush=True)

	category_count = scalar(mysql, "SELECT COUNT(*) FROM categories")
	if category_count < args.categories:
		start_id = scalar(mysql, "SELECT COALESCE(MAX(id), 0) + 1 FROM categories")
		values = []
		for offset in range(args.categories - category_count):
			category_id = start_id + offset
			name = CATEGORY_NAMES[(category_id - 1) % len(CATEGORY_NAMES)]
			if category_id > len(CATEGORY_NAMES):
				name = f"{name}-{category_id}"
			values.append(f"({category_id}, {sql_string(name)})")
		insert_values(mysql, "INSERT INTO categories (id, name) VALUES\n", values)
		print(f"categories: added {len(values):,}", flush=True)

	product_count = scalar(mysql, "SELECT COUNT(*) FROM products")
	if product_count < args.products:
		start_id = scalar(mysql, "SELECT COALESCE(MAX(id), 0) + 1 FROM products")
		values = []
		for offset in range(args.products - product_count):
			product_id = start_id + offset
			name = (
				f"{PRODUCT_PREFIXES[product_id % len(PRODUCT_PREFIXES)]}"
				f"{PRODUCT_TYPES[(product_id * 7) % len(PRODUCT_TYPES)]}-{product_id:04d}"
			)
			price_cents = 500 + ((product_id * 7_919) % 499_500)
			price = Decimal(price_cents) / 100
			stock = 1_000 + ((product_id * 113) % 49_000)
			created = date(2022, 1, 1) + timedelta(days=(product_id * 13) % 1_095)
			values.append(
				f"({product_id}, {sql_string(name)}, {price:.2f}, {stock}, '{created.isoformat()} 08:00:00')"
			)
		insert_values(mysql, "INSERT INTO products (id, name, price, stock, created_at) VALUES\n", values)
		print(f"products: added {len(values):,}", flush=True)

	category_ids = [int(row[0]) for row in mysql.query("SELECT id FROM categories ORDER BY id")]
	product_ids = [int(row[0]) for row in mysql.query("SELECT id FROM products ORDER BY id")]
	relations = []
	for index, product_id in enumerate(product_ids):
		first = category_ids[(index * 7) % len(category_ids)]
		second = category_ids[(index * 11 + 3) % len(category_ids)]
		relations.append(f"({product_id}, {first})")
		if second != first:
			relations.append(f"({product_id}, {second})")
	insert_values(mysql, "INSERT IGNORE INTO product_categories (product_id, category_id) VALUES\n", relations)
	print(f"product_categories: ensured {len(relations):,} deterministic links", flush=True)


def normalize_existing_totals(mysql: DockerMysql) -> None:
	mysql.execute(
		"""
UPDATE orders o
JOIN (
    SELECT order_id, ROUND(SUM(quantity * unit_price), 2) AS item_total
    FROM order_items
    GROUP BY order_id
) i ON i.order_id = o.id
SET o.total_amount = i.item_total
WHERE ABS(o.total_amount - i.item_total) >= 0.01;
"""
	)


def order_timestamp(order_id: int, days: list[str]) -> str:
	day = days[(order_id * 37) % len(days)]
	seconds = (order_id * 7_919) % 86_400
	return f"{day} {seconds // 3_600:02d}:{(seconds % 3_600) // 60:02d}:{seconds % 60:02d}"


def order_status(order_id: int) -> str:
	bucket = (order_id * 97) % 100
	if bucket < 68:
		return "completed"
	if bucket < 83:
		return "pending"
	if bucket < 94:
		return "cancelled"
	return "refunded"


def top_up_orders(mysql: DockerMysql, args: argparse.Namespace) -> None:
	current_count = scalar(mysql, "SELECT COUNT(*) FROM orders")
	remaining = args.orders - current_count
	if remaining <= 0:
		print(f"orders: already at {current_count:,}", flush=True)
		return

	start_id = scalar(mysql, "SELECT COALESCE(MAX(id), 0) + 1 FROM orders")
	user_ids = [int(row[0]) for row in mysql.query("SELECT id FROM users ORDER BY id")]
	products = [
		(int(row[0]), int((Decimal(row[1]) * 100).to_integral_value()))
		for row in mysql.query("SELECT id, price FROM products ORDER BY id")
	]
	days = [(date.today() - timedelta(days=day)).isoformat() for day in range(1_096)]
	process = mysql.open_stream()
	assert process.stdin is not None
	process.stdin.write("SET SESSION foreign_key_checks = 0; SET SESSION unique_checks = 0;\n")

	generated = 0
	try:
		while generated < remaining:
			batch_count = min(args.batch_size, remaining - generated)
			order_values = []
			item_values = []
			for offset in range(batch_count):
				order_id = start_id + generated + offset
				user_id = user_ids[(order_id * 47) % len(user_ids)]
				item_count = 1 + ((order_id * 13) % 3)
				total_cents = 0
				for item_index in range(item_count):
					product_id, unit_cents = products[
						(order_id * 31 + item_index * 137) % len(products)
					]
					quantity = 1 + ((order_id + item_index * 5) % 4)
					total_cents += unit_cents * quantity
					item_values.append(
						f"({order_id}, {product_id}, {quantity}, {Decimal(unit_cents) / 100:.2f})"
					)
				order_values.append(
					f"({order_id}, {user_id}, '{order_timestamp(order_id, days)}', "
					f"{Decimal(total_cents) / 100:.2f}, '{order_status(order_id)}')"
				)

			process.stdin.write("START TRANSACTION;\n")
			process.stdin.write(
				"INSERT INTO orders (id, user_id, order_date, total_amount, status) VALUES\n"
				+ ",\n".join(order_values)
				+ ";\n"
			)
			process.stdin.write(
				"INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES\n"
				+ ",\n".join(item_values)
				+ ";\nCOMMIT;\n"
			)
			process.stdin.flush()
			generated += batch_count
			if generated % 50_000 == 0 or generated == remaining:
				print(f"orders: generated {generated:,}/{remaining:,}", flush=True)
	finally:
		process.stdin.close()
	return_code = process.wait()
	if return_code != 0:
		raise RuntimeError(f"mysql exited with status {return_code}; rerun the script to resume")


def verify(mysql: DockerMysql, args: argparse.Namespace) -> None:
	counts = mysql.query(
		"""
SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'product_categories', COUNT(*) FROM product_categories
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items;
"""
	)
	print("\nfinal counts:", flush=True)
	for table, count in counts:
		print(f"  {table:20s} {int(count):,}", flush=True)

	checks = mysql.query(
		"""
SELECT 'orders_without_user', COUNT(*)
FROM orders o LEFT JOIN users u ON u.id = o.user_id WHERE u.id IS NULL
UNION ALL
SELECT 'items_without_order', COUNT(*)
FROM order_items i LEFT JOIN orders o ON o.id = i.order_id WHERE o.id IS NULL
UNION ALL
SELECT 'items_without_product', COUNT(*)
FROM order_items i LEFT JOIN products p ON p.id = i.product_id WHERE p.id IS NULL
UNION ALL
SELECT 'categories_without_product', COUNT(*)
FROM product_categories pc LEFT JOIN products p ON p.id = pc.product_id WHERE p.id IS NULL
UNION ALL
SELECT 'categories_without_category', COUNT(*)
FROM product_categories pc LEFT JOIN categories c ON c.id = pc.category_id WHERE c.id IS NULL
UNION ALL
SELECT 'orders_without_items', COUNT(*)
FROM orders o LEFT JOIN order_items i ON i.order_id = o.id WHERE i.id IS NULL;
"""
	)
	failed = [(name, int(count)) for name, count in checks if int(count) != 0]
	print("\nrelationship checks:", flush=True)
	for name, count in checks:
		print(f"  {name:30s} {int(count):,}", flush=True)
	if failed:
		raise RuntimeError(f"relationship verification failed: {failed}")
	if scalar(mysql, "SELECT COUNT(*) FROM users") < args.users:
		raise RuntimeError("user target was not reached")
	if scalar(mysql, "SELECT COUNT(*) FROM orders") < args.orders:
		raise RuntimeError("order target was not reached")


def main() -> int:
	args = parse_args()
	if min(args.users, args.orders, args.products, args.categories, args.batch_size) <= 0:
		raise ValueError("all target sizes and batch-size must be positive")
	mysql = DockerMysql(args)
	print(
		f"target: users={args.users:,}, orders={args.orders:,}, products={args.products:,}, "
		f"categories={args.categories:,}",
		flush=True,
	)
	top_up_dimensions(mysql, args)
	normalize_existing_totals(mysql)
	top_up_orders(mysql, args)
	verify(mysql, args)
	return 0


if __name__ == "__main__":
	try:
		raise SystemExit(main())
	except (subprocess.CalledProcessError, RuntimeError, ValueError) as error:
		print(f"error: {error}", file=sys.stderr, flush=True)
		raise SystemExit(1)
