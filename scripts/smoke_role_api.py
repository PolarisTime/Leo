#!/usr/bin/env python3
import copy
import json
import os
import sys
import time

import requests


BASE_URL = "http://127.0.0.1:11211/api"
PASSWORD = os.environ.get("LEO_SMOKE_PASSWORD", "")
TIMEOUT = 120
SUFFIX = str(time.time_ns())[-10:]


ROLE_CASES = [
    {
        "name": "财务主管",
        "role_name": "财务主管",
        "allowed_reads": [
            "/receipts",
            "/payments",
            "/invoice-receipts",
            "/invoice-issues",
            "/pending-invoice-receipt-report",
            "/company-settings",
            "/receivables-payables",
        ],
        "denied_reads": [
            "/purchase-orders",
            "/sales-orders",
        ],
        "write_path": "/invoice-receipts",
    },
    {
        "name": "销售主管",
        "role_name": "销售经理",
        "allowed_reads": [
            "/customers",
            "/sales-orders",
            "/sales-outbounds",
            "/sales-contracts",
            "/customer-statements",
        ],
        "denied_reads": [
            "/invoice-issues",
            "/payments",
            "/pending-invoice-receipt-report",
        ],
        "write_path": "/sales-contracts",
    },
    {
        "name": "采购专员",
        "role_name": "采购专员",
        "allowed_reads": [
            "/materials",
            "/suppliers",
            "/warehouses",
            "/purchase-orders",
            "/purchase-inbounds",
            "/purchase-contracts",
            "/pending-invoice-receipt-report",
        ],
        "denied_reads": [
            "/invoice-receipts",
            "/payments",
            "/sales-orders",
        ],
        "write_path": "/purchase-orders",
    },
]


class RoleSmokeRunner:
    def __init__(self):
        if not PASSWORD:
            raise RuntimeError("缺少环境变量 LEO_SMOKE_PASSWORD")
        self.session = requests.Session()
        self.results = []
        self.created_user_ids = []
        self.admin_token = None
        self.default_warehouse_name = None

    def add(self, role_name, name, method, path, ok, status=None, code=None, message=None):
        self.results.append(
            {
                "role": role_name,
                "name": name,
                "method": method,
                "path": path,
                "ok": ok,
                "status": status,
                "code": code,
                "message": message,
            }
        )

    def request(self, method, path, token, params=None, body=None):
        response = self.session.request(
            method,
            f"{BASE_URL}{path}",
            headers={"Authorization": f"Bearer {token}"},
            params=params,
            json=body,
            timeout=TIMEOUT,
        )
        payload = None
        if "application/json" in response.headers.get("content-type", ""):
            payload = response.json()
        return response, payload

    def login(self, login_name):
        response = self.session.post(
            f"{BASE_URL}/auth/login",
            json={"loginName": login_name, "password": PASSWORD},
            timeout=TIMEOUT,
        )
        payload = response.json()
        ok = response.status_code == 200 and payload.get("code") == 0 and payload.get("data", {}).get("accessToken")
        return ok, response, payload

    def ensure_admin_token(self):
        if self.admin_token:
            return True
        ok, response, payload = self.login("admin")
        self.add("管理员", "login", "POST", "/auth/login", ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)
        if not ok:
            return False
        self.admin_token = payload["data"]["accessToken"]
        return True

    def resolve_default_warehouse_name(self):
        if self.default_warehouse_name:
            return self.default_warehouse_name
        if not self.ensure_admin_token():
            return "一号库"
        response, payload = self.request("GET", "/warehouses", self.admin_token, params={"page": 0, "size": 1})
        if response.status_code == 200 and payload and payload.get("code") == 0:
            records = payload.get("data", {}).get("records") or []
            if records and isinstance(records[0], dict) and records[0].get("warehouseName"):
                self.default_warehouse_name = records[0]["warehouseName"]
                return self.default_warehouse_name
        return "一号库"

    def create_role_user(self, role_case):
        if not self.ensure_admin_token():
            return None
        login_name = f"role_{role_case['write_path'].strip('/').replace('-', '_')}_{SUFFIX}"
        mobile_suffix = str(len(self.created_user_ids) + 1)
        response, payload = self.request(
            "POST",
            "/user-accounts",
            self.admin_token,
            body={
                "loginName": login_name,
                "userName": f"{role_case['name']} Smoke",
                "mobile": "1" + (SUFFIX[:-1] + mobile_suffix).zfill(10)[:10],
                "roleNames": [role_case["role_name"]],
                "dataScope": "全部",
                "permissionSummary": "",
                "status": "正常",
                "remark": f"role-smoke-{SUFFIX}",
            },
        )
        ok = response.status_code == 200 and payload and payload.get("code") == 0 and payload.get("data", {}).get("id")
        self.add(role_case["name"], "bootstrap user", "POST", "/user-accounts", ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)
        if not ok:
            return None
        self.created_user_ids.append(payload["data"]["id"])
        return login_name

    def expect_read_allowed(self, role_name, token, path):
        response, payload = self.request("GET", path, token, params={"page": 0, "size": 3})
        ok = response.status_code == 200 and payload and payload.get("code") == 0
        self.add(role_name, f"{path} allowed", "GET", path, ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)

    def expect_read_denied(self, role_name, token, path):
        response, payload = self.request("GET", path, token, params={"page": 0, "size": 3})
        ok = response.status_code in (401, 403) or (payload is not None and payload.get("code") != 0)
        self.add(role_name, f"{path} denied", "GET", path, ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)

    def get_first_detail(self, token, path):
        response, payload = self.request("GET", path, token, params={"page": 0, "size": 1})
        if response.status_code != 200 or not payload or payload.get("code") != 0:
            return None
        records = payload.get("data", {}).get("records") or []
        if not records or not isinstance(records[0], dict) or not records[0].get("id"):
            return None
        detail_response, detail_payload = self.request("GET", f"{path}/{records[0]['id']}", token)
        if detail_response.status_code != 200 or not detail_payload or detail_payload.get("code") != 0:
            return None
        return detail_payload["data"]

    def strip_item_ids(self, payload):
        for item in payload.get("items") or []:
            if isinstance(item, dict):
                item.pop("id", None)
                item.pop("lineNo", None)
        return payload

    def map_common_items(self, payload):
        return [
            {
                "materialCode": item.get("materialCode"),
                "brand": item.get("brand"),
                "category": item.get("category"),
                "material": item.get("material"),
                "spec": item.get("spec"),
                "length": item.get("length"),
                "unit": item.get("unit"),
                "warehouseName": item.get("warehouseName") or self.resolve_default_warehouse_name(),
                "batchNo": item.get("batchNo"),
                "quantity": item.get("quantity"),
                "quantityUnit": item.get("quantityUnit"),
                "pieceWeightTon": item.get("pieceWeightTon"),
                "piecesPerBundle": item.get("piecesPerBundle"),
                "weightTon": item.get("weightTon"),
                "unitPrice": item.get("unitPrice"),
                "amount": item.get("amount"),
            }
            for item in payload.get("items") or []
            if isinstance(item, dict)
        ]

    def map_invoice_receipt_items(self, payload):
        return [
            {
                **item_payload,
                "sourceNo": item.get("sourceNo"),
                "sourcePurchaseOrderItemId": item.get("sourcePurchaseOrderItemId"),
            }
            for item, item_payload in zip(payload.get("items") or [], self.map_common_items(payload))
            if isinstance(item, dict)
        ]

    def mutate_payload(self, path, payload):
        if path == "/invoice-receipts":
            return {
                "receiveNo": f"RF{SUFFIX}",
                "invoiceNo": f"RINV{SUFFIX[-8:]}",
                "sourcePurchaseOrderNos": payload.get("sourcePurchaseOrderNos"),
                "supplierName": payload.get("supplierName"),
                "invoiceTitle": payload.get("invoiceTitle") or payload.get("supplierName"),
                "invoiceDate": payload.get("invoiceDate"),
                "invoiceType": payload.get("invoiceType"),
                "amount": payload.get("amount"),
                "taxAmount": payload.get("taxAmount"),
                "status": payload.get("status") or "草稿",
                "operatorName": payload.get("operatorName"),
                "remark": f"role-smoke-{SUFFIX}",
                "items": self.map_invoice_receipt_items(payload),
            }
        if path == "/sales-contracts":
            return {
                "contractNo": f"SC{SUFFIX}",
                "customerName": payload.get("customerName"),
                "projectName": payload.get("projectName"),
                "signDate": payload.get("signDate"),
                "effectiveDate": payload.get("effectiveDate"),
                "expireDate": payload.get("expireDate"),
                "salesName": payload.get("salesName"),
                "status": payload.get("status") or "执行中",
                "remark": f"role-smoke-{SUFFIX}",
                "items": self.map_common_items(payload),
            }
        if path == "/purchase-orders":
            return {
                "orderNo": f"PO{SUFFIX}",
                "supplierName": payload.get("supplierName"),
                "orderDate": payload.get("orderDate"),
                "buyerName": payload.get("buyerName"),
                "status": payload.get("status") or "草稿",
                "remark": f"role-smoke-{SUFFIX}",
                "items": self.map_common_items(payload),
            }
        return payload

    def run_representative_write(self, role_name, token, path):
        detail = self.get_first_detail(token, path)
        if not detail:
            self.add(role_name, f"{path} write", "POST", path, False, message="missing source detail")
            return
        payload = self.mutate_payload(path, detail)
        response, body = self.request("POST", path, token, body=payload)
        ok = response.status_code == 200 and body and body.get("code") == 0 and body.get("data", {}).get("id")
        self.add(role_name, f"{path} create", "POST", path, ok, response.status_code, body.get("code") if body else None, body.get("message") if body else None)
        if not ok:
            return
        record_id = body["data"]["id"]
        delete_response, delete_payload = self.request("DELETE", f"{path}/{record_id}", token)
        delete_ok = delete_response.status_code == 200 and delete_payload and delete_payload.get("code") == 0
        self.add(role_name, f"{path} delete", "DELETE", f"{path}/{record_id}", delete_ok, delete_response.status_code, delete_payload.get("code") if delete_payload else None, delete_payload.get("message") if delete_payload else None)

    def run_role_case(self, role_case):
        login_name = self.create_role_user(role_case)
        if not login_name:
            return
        ok, response, payload = self.login(login_name)
        self.add(role_case["name"], "login", "POST", "/auth/login", ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)
        if not ok:
            return
        token = payload["data"]["accessToken"]

        ping_response, ping_payload = self.request("GET", "/auth/ping", token)
        ping_ok = ping_response.status_code == 200 and ping_payload and ping_payload.get("code") == 0
        self.add(role_case["name"], "auth ping", "GET", "/auth/ping", ping_ok, ping_response.status_code, ping_payload.get("code") if ping_payload else None, ping_payload.get("message") if ping_payload else None)

        for path in role_case["allowed_reads"]:
            self.expect_read_allowed(role_case["name"], token, path)

        for path in role_case["denied_reads"]:
            self.expect_read_denied(role_case["name"], token, path)

        self.run_representative_write(role_case["name"], token, role_case["write_path"])

    def run(self):
        if not self.ensure_admin_token():
            return {
                "total": len(self.results),
                "passed": sum(1 for item in self.results if item["ok"]),
                "failed": sum(1 for item in self.results if not item["ok"]),
                "failures": [item for item in self.results if not item["ok"]],
            }
        for role_case in ROLE_CASES:
            self.run_role_case(role_case)
        for user_id in reversed(self.created_user_ids):
            response, payload = self.request("DELETE", f"/user-accounts/{user_id}", self.admin_token)
            ok = response.status_code == 200 and payload and payload.get("code") == 0
            self.add("管理员", "cleanup user", "DELETE", f"/user-accounts/{user_id}", ok, response.status_code, payload.get("code") if payload else None, payload.get("message") if payload else None)
        return {
            "total": len(self.results),
            "passed": sum(1 for item in self.results if item["ok"]),
            "failed": sum(1 for item in self.results if not item["ok"]),
            "failures": [item for item in self.results if not item["ok"]],
        }


def main():
    runner = RoleSmokeRunner()
    summary = runner.run()
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
