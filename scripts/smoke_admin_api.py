#!/usr/bin/env python3
import argparse
import base64
import copy
import hashlib
import hmac
import json
import os
import re
import struct
import sys
import time
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import requests


BASE_URL = "http://127.0.0.1:11211/api"
LOGIN_NAME = "admin"
PASSWORD = os.environ.get("LEO_SMOKE_PASSWORD", "")
TIMEOUT = 180
SUFFIX = str(time.time_ns())[-12:]

PAGE_PATHS = [
    "/materials",
    "/suppliers",
    "/customers",
    "/carriers",
    "/warehouses",
    "/settlement-accounts",
    "/purchase-orders",
    "/purchase-inbounds",
    "/sales-orders",
    "/sales-outbounds",
    "/freight-bills",
    "/purchase-contracts",
    "/sales-contracts",
    "/supplier-statements",
    "/customer-statements",
    "/freight-statements",
    "/receipts",
    "/payments",
    "/invoice-receipts",
    "/invoice-issues",
    "/pending-invoice-receipt-report",
    "/general-settings",
    "/company-settings",
    "/role-settings",
    "/permission-management",
    "/user-accounts",
    "/auth/refresh-tokens",
    "/auth/api-keys",
    "/operation-logs",
    "/inventory-report",
    "/io-report",
    "/receivables-payables",
]

DETAIL_SKIP = {
    "/operation-logs",
    "/inventory-report",
    "/io-report",
    "/receivables-payables",
    "/pending-invoice-receipt-report",
    "/auth/refresh-tokens",
    "/print-templates",
}

CRUD_SIMPLE = {
    "/materials": "MaterialRequest",
    "/suppliers": "SupplierRequest",
    "/customers": "CustomerRequest",
    "/carriers": "CarrierRequest",
    "/warehouses": "WarehouseRequest",
    "/settlement-accounts": "SettlementAccountRequest",
    "/supplier-statements": "SupplierStatementRequest",
    "/customer-statements": "CustomerStatementRequest",
    "/receipts": "ReceiptRequest",
    "/payments": "PaymentRequest",
    "/invoice-receipts": "InvoiceReceiptRequest",
    "/invoice-issues": "InvoiceIssueRequest",
    "/general-settings": "NoRuleRequest",
    "/role-settings": "RoleSettingRequest",
    "/print-templates": "PrintTemplateRequest",
}

CRUD_WITH_ITEMS = {
    "/purchase-orders": "PurchaseOrderRequest",
    "/purchase-inbounds": "PurchaseInboundRequest",
    "/sales-orders": "SalesOrderRequest",
    "/sales-outbounds": "SalesOutboundRequest",
    "/freight-bills": "FreightBillRequest",
    "/purchase-contracts": "PurchaseContractRequest",
    "/sales-contracts": "SalesContractRequest",
}

SKIPPED = [
    {"path": "/auth/refresh-tokens/revoke-all", "reason": "invalidates all active sessions"},
    {"path": "/system/security-keys/jwt/rotate", "reason": "rotates JWT master key"},
    {"path": "/system/security-keys/totp/rotate", "reason": "rotates TOTP master key"},
]


class SmokeRunner:
    def __init__(self, auth_mode="jwt"):
        if not PASSWORD:
            raise RuntimeError("缺少环境变量 LEO_SMOKE_PASSWORD")
        self.auth_mode = auth_mode
        self.session = requests.Session()
        self.results = []
        self.created = []
        self.first_records = {}
        self.generated = {}
        self.admin_token = None
        self.bootstrap_token = None
        self.bootstrap_user_id = None
        self.bootstrap_temp_user_id = None
        self.bootstrap_temp_login = None
        self.bootstrap_temp_secret = None
        self.bootstrap_operator_token = None
        self.api_key = None
        self.bootstrap_api_key_id = None
        self.spec = requests.get(f"{BASE_URL}/api-docs", timeout=60).json()
        self.schemas = self.spec["components"]["schemas"]

    def add(self, name, method, path, ok, category="ok", status=None, code=None, message=None, note=None):
        self.results.append(
            {
                "name": name,
                "method": method,
                "path": path,
                "ok": ok,
                "category": category,
                "status": status,
                "code": code,
                "message": message,
                "note": note,
            }
        )

    def login(self, user_agent=None):
        headers = {}
        if user_agent:
            headers["User-Agent"] = user_agent
        response = requests.post(
            f"{BASE_URL}/auth/login",
            json={"loginName": LOGIN_NAME, "password": PASSWORD},
            headers=headers,
            timeout=60,
        )
        return response, response.json()

    def login_with_2fa(self, login_name, password, secret, user_agent=None):
        headers = {}
        if user_agent:
            headers["User-Agent"] = user_agent
        response = requests.post(
            f"{BASE_URL}/auth/login",
            json={"loginName": login_name, "password": password},
            headers=headers,
            timeout=60,
        )
        payload = response.json()
        if response.status_code != 200 or payload.get("code") != 0:
            return response, payload
        step1 = payload.get("data") or {}
        if not isinstance(step1, dict) or not step1.get("requires2fa") or not step1.get("tempToken"):
            return response, payload
        verify = requests.post(
            f"{BASE_URL}/auth/login-2fa",
            json={"tempToken": step1["tempToken"], "totpCode": self.totp(secret)},
            headers=headers,
            timeout=60,
        )
        return verify, verify.json()

    def request(self, method, path, token=None, api_key=None, params=None, body=None, files=None, timeout=TIMEOUT, extra_headers=None):
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if api_key:
            headers["X-API-Key"] = api_key
        if extra_headers:
            headers.update(extra_headers)
        response = self.session.request(
            method,
            f"{BASE_URL}{path}",
            params=params,
            json=body,
            files=files,
            headers=headers,
            timeout=timeout,
        )
        payload = None
        if "application/json" in response.headers.get("content-type", ""):
            payload = response.json()
        return response, payload

    def protected_auth(self):
        if self.auth_mode == "apikey":
            return {"api_key": self.api_key}
        return {"token": self.admin_token}

    def protected_headers(self):
        if self.auth_mode == "apikey":
            return {"X-API-Key": self.api_key}
        return {"Authorization": f"Bearer {self.admin_token}"}

    def deref(self, schema):
        if not schema:
            return {}
        if "$ref" in schema:
            return self.deref(self.schemas[schema["$ref"].split("/")[-1]])
        if "allOf" in schema:
            merged = {"type": "object", "properties": {}, "required": []}
            for part in schema["allOf"]:
                current = self.deref(part)
                merged["properties"].update(current.get("properties", {}))
                merged["required"].extend(current.get("required", []))
            return merged
        return schema

    def pick(self, data, schema):
        schema = self.deref(schema)
        if data is None:
            return None
        if schema.get("type") == "array":
            return [self.pick(item, schema.get("items", {})) for item in data]
        if schema.get("type") == "object" or "properties" in schema:
            source = data if isinstance(data, dict) else {}
            return {key: self.pick(source[key], sub) for key, sub in schema.get("properties", {}).items() if key in source}
        return data

    def mutate(self, payload, path):
        payload = copy.deepcopy(payload or {})
        if path == "/user-accounts":
            payload["loginName"] = f"smoke_{SUFFIX}"
            payload["userName"] = f"Smoke User {SUFFIX}"
            payload["mobile"] = "1" + SUFFIX.zfill(10)[:10]
            payload["status"] = payload.get("status") or "正常"
            return payload
        if path == "/role-settings":
            payload["roleCode"] = f"SMOKE_{SUFFIX}"
            payload["roleName"] = f"Smoke Role {SUFFIX}"
        if path == "/print-templates":
            payload["billType"] = payload.get("billType") or "purchase-orders"
            payload["templateName"] = f"smoke-template-{SUFFIX}"
            payload["templateHtml"] = payload.get("templateHtml") or "<div>smoke</div>"
            payload["isDefault"] = payload.get("isDefault") if payload.get("isDefault") in ("0", "1") else "0"
            return payload
        for key, value in list(payload.items()):
            if not isinstance(value, str):
                continue
            lower = key.lower()
            if lower in {"materialcode", "suppliercode", "customercode", "carriercode", "warehousecode", "settingcode", "rolecode"}:
                payload[key] = re.sub(r"[^A-Za-z0-9_-]+", "", value or "SMOKE")[:24] + SUFFIX
            elif lower.endswith("no") or lower == "loginname":
                payload[key] = re.sub(r"[^A-Za-z0-9_-]+", "", value or "SMOKE")[:24] + SUFFIX
            elif lower == "mobile":
                payload[key] = "1" + SUFFIX.zfill(10)[:10]
            elif lower == "remark":
                payload[key] = f"smoke-{SUFFIX}"
        return payload

    def strip_item_ids(self, payload):
        items = payload.get("items")
        if not isinstance(items, list):
            return payload
        for item in items:
            if isinstance(item, dict):
                item.pop("id", None)
                item.pop("lineNo", None)
        return payload

    def parse_query_value(self, url, key):
        parsed = urlparse(url)
        values = parse_qs(parsed.query).get(key, [])
        return values[0] if values else None

    def create_record(self, path, payload, fetch_detail=True):
        response, body = self.request("POST", path, body=payload, **self.protected_auth())
        ok = response.status_code == 200 and body.get("code") == 0
        self.add(path, "POST", path, ok, category="write" if ok else "fail", status=response.status_code, code=body.get("code") if body else None, message=body.get("message") if body else None)
        if not ok or not isinstance(body.get("data"), dict) or body["data"].get("id") is None:
            return None
        record = body["data"]
        self.created.append((path, record["id"]))
        if fetch_detail:
            detail, detail_payload = self.request("GET", f"{path}/{record['id']}", **self.protected_auth())
            if detail.status_code == 200 and detail_payload.get("code") == 0 and isinstance(detail_payload.get("data"), dict):
                record = detail_payload["data"]
        self.first_records[path] = record
        self.generated[path] = record
        return record

    def update_record(self, path, item_id, payload):
        response, body = self.request("PUT", f"{path}/{item_id}", body=payload, **self.protected_auth())
        ok = response.status_code == 200 and body.get("code") == 0
        self.add(path, "PUT", f"{path}/{item_id}", ok, category="write" if ok else "fail", status=response.status_code, code=body.get("code") if body else None, message=body.get("message") if body else None)
        return ok

    def create_and_update_record(self, path, payload, update_payload=None):
        record = self.create_record(path, payload)
        if not record:
            return None
        next_payload = copy.deepcopy(update_payload if update_payload is not None else payload)
        if "remark" in next_payload:
            next_payload["remark"] = f"updated-{SUFFIX}"
        if self.update_record(path, record["id"], next_payload):
            detail, detail_payload = self.request("GET", f"{path}/{record['id']}", **self.protected_auth())
            if detail.status_code == 200 and detail_payload.get("code") == 0 and isinstance(detail_payload.get("data"), dict):
                record = detail_payload["data"]
                self.first_records[path] = record
                self.generated[path] = record
        return self.first_records.get(path, record)

    def apply_material_fields(self, item, material_record):
        item["materialCode"] = material_record["materialCode"]
        item["brand"] = material_record["brand"]
        item["category"] = material_record["category"]
        item["material"] = material_record["material"]
        item["spec"] = material_record["spec"]
        item["length"] = material_record.get("length")
        item["unit"] = material_record["unit"]
        if "quantityUnit" in item and material_record.get("quantityUnit"):
            item["quantityUnit"] = material_record["quantityUnit"]
        if "materialName" in item:
            item["materialName"] = f"{material_record['category']}{material_record['spec']}"
        return item

    def build_simple_payload_from_sample(self, path, schema_name):
        source = self.first_records.get(path)
        if not source:
            return None
        return self.mutate(self.pick(source, {"$ref": f"#/components/schemas/{schema_name}"}) or {}, path)

    def build_item_payload_from_sample(self, path, schema_name):
        source = self.first_records.get(path)
        if not source:
            return None
        return self.strip_item_ids(self.mutate(self.pick(source, {"$ref": f"#/components/schemas/{schema_name}"}) or {}, path))

    def run(self):
        if self.auth_mode == "apikey":
            self.prepare_api_key_mode()
            if not self.api_key:
                return self.summary()
        else:
            response, payload = self.login()
            self.admin_token = payload["data"]["accessToken"]
            self.add("login", "POST", "/auth/login", response.status_code == 200 and payload.get("code") == 0, category="auth", status=response.status_code, code=payload.get("code"), message=payload.get("message"))

        self.run_auth_smoke()
        self.run_read_smoke()
        self.run_generated_data_smoke()
        self.run_attachment_smoke()
        self.run_export_import_smoke()
        self.run_api_key_smoke()
        self.run_role_action_smoke()
        self.run_simple_crud_smoke()
        self.run_item_crud_smoke()
        self.run_freight_statement_smoke()
        self.run_user_account_smoke()
        self.run_cleanup()
        self.run_refresh_revoke_smoke()
        if self.bootstrap_api_key_id or self.bootstrap_temp_user_id:
            self.revoke_bootstrap_api_key()
        if self.auth_mode != "apikey":
            self.run_logout_smoke()
        return self.summary()

    def prepare_api_key_mode(self):
        response, payload = self.login(user_agent=f"smoke-bootstrap-{SUFFIX}")
        ok = response.status_code == 200 and payload.get("code") == 0
        self.add("bootstrap-login", "POST", "/auth/login", ok, category="setup", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if not ok:
            return
        self.bootstrap_token = payload["data"]["accessToken"]
        self.bootstrap_user_id = payload["data"]["user"]["id"]
        role_names = [
            item.strip()
            for item in str(payload["data"]["user"].get("roleName") or "").split(",")
            if item.strip()
        ]
        if not role_names:
            role_page, role_payload = self.request("GET", "/role-settings", token=self.bootstrap_token, params={"page": 0, "size": 1})
            if role_page.status_code == 200 and role_payload.get("code") == 0:
                role_names = [
                    row.get("roleName")
                    for row in role_payload.get("data", {}).get("records", [])
                    if isinstance(row, dict) and row.get("roleName")
                ][:1]
        create_user_response, create_user_payload = self.request(
            "POST",
            "/user-accounts",
            token=self.bootstrap_token,
            body={
                "loginName": f"smoke_apikey_{SUFFIX}",
                "userName": f"Smoke API Key {SUFFIX[-6:]}",
                "mobile": "1" + SUFFIX.zfill(10)[:10],
                "roleNames": role_names,
                "dataScope": "全部",
                "permissionSummary": "",
                "status": "正常",
                "remark": f"smoke-{SUFFIX}",
            },
        )
        create_user_ok = create_user_response.status_code == 200 and create_user_payload.get("code") == 0
        self.add("bootstrap-user", "POST", "/user-accounts", create_user_ok, category="setup", status=create_user_response.status_code, code=create_user_payload.get("code") if create_user_payload else None, message=create_user_payload.get("message") if create_user_payload else None)
        if not create_user_ok:
            return
        self.bootstrap_temp_user_id = create_user_payload["data"]["id"]
        self.bootstrap_temp_login = create_user_payload["data"]["loginName"]
        setup_response, setup_payload = self.request(
            "POST",
            f"/user-accounts/{self.bootstrap_temp_user_id}/2fa/setup",
            token=self.bootstrap_token,
        )
        setup_ok = setup_response.status_code == 200 and setup_payload.get("code") == 0 and setup_payload.get("data", {}).get("secret")
        self.add("bootstrap-user-2fa-setup", "POST", f"/user-accounts/{self.bootstrap_temp_user_id}/2fa/setup", setup_ok, category="setup", status=setup_response.status_code, code=setup_payload.get("code") if setup_payload else None, message=setup_payload.get("message") if setup_payload else None)
        if not setup_ok:
            return
        self.bootstrap_temp_secret = setup_payload["data"]["secret"]
        enable_response, enable_payload = self.request(
            "POST",
            f"/user-accounts/{self.bootstrap_temp_user_id}/2fa/enable",
            token=self.bootstrap_token,
            body={"totpCode": self.totp(self.bootstrap_temp_secret)},
        )
        enable_ok = enable_response.status_code == 200 and enable_payload.get("code") == 0
        self.add("bootstrap-user-2fa-enable", "POST", f"/user-accounts/{self.bootstrap_temp_user_id}/2fa/enable", enable_ok, category="setup", status=enable_response.status_code, code=enable_payload.get("code") if enable_payload else None, message=enable_payload.get("message") if enable_payload else None)
        if not enable_ok:
            return
        operator_login_response, operator_login_payload = self.login_with_2fa(
            self.bootstrap_temp_login,
            PASSWORD,
            self.bootstrap_temp_secret,
            user_agent=f"smoke-apikey-operator-{SUFFIX}",
        )
        operator_login_ok = operator_login_response.status_code == 200 and operator_login_payload.get("code") == 0 and operator_login_payload.get("data", {}).get("accessToken")
        self.add("bootstrap-user-login-2fa", "POST", "/auth/login-2fa", operator_login_ok, category="setup", status=operator_login_response.status_code, code=operator_login_payload.get("code") if operator_login_payload else None, message=operator_login_payload.get("message") if operator_login_payload else None)
        if not operator_login_ok:
            return
        self.bootstrap_operator_token = operator_login_payload["data"]["accessToken"]
        menu_options_response, menu_options_payload = self.request(
            "GET",
            "/auth/api-keys/menu-options",
            token=self.bootstrap_operator_token,
        )
        menu_codes = []
        if menu_options_response.status_code == 200 and menu_options_payload.get("code") == 0:
            menu_codes = [
                item.get("code")
                for item in menu_options_payload.get("data", [])
                if isinstance(item, dict) and item.get("code")
            ]
        action_options_response, action_options_payload = self.request(
            "GET",
            "/auth/api-keys/action-options",
            token=self.bootstrap_operator_token,
        )
        action_codes = ["VIEW", "CREATE", "EDIT", "DELETE", "EXPORT"]
        if action_options_response.status_code == 200 and action_options_payload.get("code") == 0:
            action_codes = [
                item.get("code")
                for item in action_options_payload.get("data", [])
                if isinstance(item, dict) and item.get("code")
            ] or action_codes
        response, payload = self.request(
            "POST",
            "/auth/api-keys",
            token=self.bootstrap_operator_token,
            params={"userId": self.bootstrap_temp_user_id},
            body={
                "keyName": f"bootstrap-key-{SUFFIX}",
                "usageScope": "全部接口",
                "allowedMenus": menu_codes,
                "allowedActions": action_codes,
                "expireDays": 1,
            },
            extra_headers={"X-TOTP-Code": self.totp(self.bootstrap_temp_secret)},
        )
        ok = response.status_code == 200 and payload.get("code") == 0 and payload["data"].get("rawKey")
        self.add("bootstrap-api-key", "POST", "/auth/api-keys", ok, category="setup", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if ok:
            self.bootstrap_api_key_id = payload["data"]["id"]
            self.api_key = payload["data"]["rawKey"]

    def revoke_bootstrap_api_key(self):
        if self.bootstrap_api_key_id and self.bootstrap_operator_token:
            response, payload = self.request("POST", f"/auth/api-keys/{self.bootstrap_api_key_id}/revoke", token=self.bootstrap_operator_token)
            self.add("bootstrap-api-key-revoke", "POST", f"/auth/api-keys/{self.bootstrap_api_key_id}/revoke", response.status_code == 200 and payload.get("code") == 0, category="cleanup", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if self.bootstrap_temp_user_id and self.bootstrap_token:
            response, payload = self.request("DELETE", f"/user-accounts/{self.bootstrap_temp_user_id}", token=self.bootstrap_token)
            self.add("bootstrap-user-delete", "DELETE", f"/user-accounts/{self.bootstrap_temp_user_id}", response.status_code == 200 and payload.get("code") == 0, category="cleanup", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

    def run_auth_smoke(self):
        response, payload = self.request("GET", "/auth/ping", **self.protected_auth())
        self.add("/auth/ping", "GET", "/auth/ping", response.status_code == 200 and payload.get("code") == 0, category="auth", status=response.status_code, code=payload.get("code"), message=payload.get("message"))
        health = requests.get(f"{BASE_URL}/system/health", timeout=60)
        self.add("/system/health", "GET", "/system/health", health.status_code == 200, category="auth", status=health.status_code)
        if self.auth_mode == "jwt":
            response, payload = self.login(user_agent=f"smoke-refresh-{SUFFIX}")
            refresh_token = payload["data"]["refreshToken"]
            refresh = requests.post(f"{BASE_URL}/auth/refresh", json={"refreshToken": refresh_token}, timeout=60)
            refresh_payload = refresh.json()
            self.add("/auth/refresh", "POST", "/auth/refresh", refresh.status_code == 200 and refresh_payload.get("code") == 0, category="auth", status=refresh.status_code, code=refresh_payload.get("code"), message=refresh_payload.get("message"))
        response, payload = self.request("GET", "/auth/refresh-tokens", params={"page": 0, "size": 100}, **self.protected_auth())
        self.add("/auth/refresh-tokens", "GET", "/auth/refresh-tokens", response.status_code == 200 and payload.get("code") == 0, category="read" if response.status_code == 200 else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if self.auth_mode == "jwt":
            invalid = requests.post(f"{BASE_URL}/auth/login-2fa", json={"tempToken": "invalid", "totpCode": "000000"}, timeout=60)
            invalid_payload = invalid.json()
            self.add("/auth/login-2fa", "POST", "/auth/login-2fa", invalid_payload.get("code") is not None, category="validation", status=invalid.status_code, code=invalid_payload.get("code"), message=invalid_payload.get("message"))

    def run_read_smoke(self):
        for path in PAGE_PATHS:
            response, payload = self.request("GET", path, params={"page": 0, "size": 5}, **self.protected_auth())
            ok = response.status_code == 200 and payload.get("code") == 0
            if ok and isinstance(payload.get("data"), dict):
                records = payload["data"].get("records") or []
                if records and isinstance(records[0], dict) and records[0].get("id") is not None:
                    self.first_records[path] = records[0]
            self.add(path, "GET", path, ok, category="read" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

        for path, params in [
            ("/system/menus/tree", None),
            ("/system/database/status", None),
            ("/system/security-keys", None),
            ("/auth/api-keys/user-options", None),
            ("/auth/api-keys/menu-options", None),
            ("/auth/api-keys/action-options", None),
            ("/general-settings/upload-rule", {"moduleKey": "general-settings"}),
            ("/upload-rules/page", {"moduleKey": "general-settings"}),
            ("/print-templates", {"billType": "purchase-orders"}),
            ("/print-templates/default", {"billType": "purchase-orders"}),
        ]:
            response, payload = self.request("GET", path, params=params, **self.protected_auth())
            ok = response.status_code == 200 and payload.get("code") == 0
            if ok and path == "/print-templates" and isinstance(payload.get("data"), list) and payload["data"]:
                self.first_records[path] = payload["data"][0]
            self.add(path, "GET", path, ok, category="read" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

        for path, row in list(self.first_records.items()):
            if path in DETAIL_SKIP:
                continue
            detail_path = f"{path}/{row['id']}"
            response, payload = self.request("GET", detail_path, **self.protected_auth())
            ok = response.status_code == 200 and payload.get("code") == 0
            if ok and isinstance(payload.get("data"), dict):
                self.first_records[path] = payload["data"]
            self.add(detail_path, "GET", detail_path, ok, category="read" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

        role = self.first_records.get("/role-settings")
        if role and role.get("id"):
            response, payload = self.request("GET", f"/role-settings/{role['id']}/actions", **self.protected_auth())
            self.add("/role-settings/{id}/actions", "GET", f"/role-settings/{role['id']}/actions", response.status_code == 200 and payload.get("code") == 0, category="read" if response.status_code == 200 else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

    def run_attachment_smoke(self):
        file_path = Path("/tmp/leo_smoke.txt")
        file_path.write_text("leo smoke\n", encoding="utf-8")
        with file_path.open("rb") as handle:
            response, payload = self.request(
                "POST",
                "/attachments/upload",
                params={"moduleKey": "materials", "sourceType": "PAGE_UPLOAD"},
                files={"file": ("leo_smoke.txt", handle, "text/plain")},
                timeout=60,
                **self.protected_auth(),
            )
        ok = response.status_code == 200 and payload.get("code") == 0
        self.add("/attachments/upload", "POST", "/attachments/upload", ok, category="write" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if not ok:
            return

        attachment_id = payload["data"]["id"]
        access_key = self.parse_query_value(payload["data"]["downloadUrl"], "accessKey")

        response = self.session.get(
            f"{BASE_URL}/attachments/{attachment_id}/download",
            headers=self.protected_headers(),
            params={"moduleKey": "materials", "accessKey": access_key},
            timeout=60,
        )
        self.add("/attachments/{id}/download", "GET", f"/attachments/{attachment_id}/download", response.status_code == 200, category="read" if response.status_code == 200 else "fail", status=response.status_code, note=response.headers.get("content-type"))

        preview = self.session.get(
            f"{BASE_URL}/attachments/{attachment_id}/preview",
            headers=self.protected_headers(),
            params={"moduleKey": "materials", "accessKey": access_key},
            timeout=60,
        )
        preview_ok = preview.status_code == 200 or ("application/json" in preview.headers.get("content-type", "") and "不支持预览" in preview.text)
        self.add("/attachments/{id}/preview", "GET", f"/attachments/{attachment_id}/preview", preview_ok, category="read" if preview_ok else "fail", status=preview.status_code, note=preview.headers.get("content-type"))

        source = self.first_records.get("/materials")
        if source and source.get("id"):
            response, payload = self.request("GET", "/attachments/bindings", params={"moduleKey": "materials", "recordId": source["id"]}, **self.protected_auth())
            ok = response.status_code == 200 and payload.get("code") == 0
            self.add("/attachments/bindings", "GET", "/attachments/bindings", ok, category="read" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
            if ok:
                attachment_ids = [item["id"] for item in payload["data"].get("attachments") or []]
                if attachment_id not in attachment_ids:
                    attachment_ids.append(attachment_id)
                update, update_payload = self.request(
                    "PUT",
                    "/attachments/bindings",
                    body={"moduleKey": "materials", "recordId": source["id"], "attachmentIds": attachment_ids},
                    **self.protected_auth(),
                )
                self.add("/attachments/bindings", "PUT", "/attachments/bindings", update.status_code == 200 and update_payload.get("code") == 0, category="write" if update.status_code == 200 else "fail", status=update.status_code, code=update_payload.get("code") if update_payload else None, message=update_payload.get("message") if update_payload else None)

    def run_export_import_smoke(self):
        export_paths = ["/materials/export"]
        for path in export_paths:
            response = self.session.post(f"{BASE_URL}{path}", headers=self.protected_headers(), timeout=300)
            self.add(path, "POST", path, response.status_code == 200 and len(response.content) > 0, category="write" if response.status_code == 200 else "fail", status=response.status_code, note=f"bytes={len(response.content)}")

        if self.auth_mode == "jwt":
            self.ensure_sensitive_operator()
            if self.bootstrap_operator_token and self.bootstrap_temp_secret:
                response, payload = self.request(
                    "POST",
                    "/system/database/export-tasks",
                    token=self.bootstrap_operator_token,
                    extra_headers={"X-TOTP-Code": self.totp(self.bootstrap_temp_secret)},
                )
                ok = response.status_code == 200 and payload.get("code") == 0 and payload.get("data", {}).get("id")
                self.add("/system/database/export-tasks", "POST", "/system/database/export-tasks", ok, category="write" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

        empty_file = Path("/tmp/leo_empty.txt")
        empty_file.write_text("", encoding="utf-8")
        import_paths = ["/materials/import"]
        if self.auth_mode == "jwt":
            import_paths.append("/system/database/import")
        for path in import_paths:
            with empty_file.open("rb") as handle:
                headers = self.protected_headers()
                data = None
                if path == "/system/database/import":
                    self.ensure_sensitive_operator()
                    if not self.bootstrap_operator_token or not self.bootstrap_temp_secret:
                        self.add(path, "POST", path, False, category="fail", message="missing sensitive operator")
                        continue
                    headers = {"Authorization": f"Bearer {self.bootstrap_operator_token}", "X-TOTP-Code": self.totp(self.bootstrap_temp_secret)}
                    data = {"databaseUsername": "leo", "databasePassword": "invalid"}
                response = self.session.post(
                    f"{BASE_URL}{path}",
                    headers=headers,
                    files={"file": ("empty.txt", handle, "text/plain")},
                    data=data,
                    timeout=60,
                )
            payload = response.json()
            self.add(path, "POST", path, payload.get("code") is not None, category="validation", status=response.status_code, code=payload.get("code"), message=payload.get("message"))

    def run_api_key_smoke(self):
        if self.auth_mode == "apikey":
            return
        self.ensure_sensitive_operator()
        if not self.bootstrap_operator_token or not self.bootstrap_temp_secret:
            self.add("/auth/api-keys", "POST", "/auth/api-keys", False, category="fail", message="missing sensitive operator")
            return
        target_user_id = self.bootstrap_temp_user_id
        if not target_user_id:
            self.add("/auth/api-keys", "POST", "/auth/api-keys", False, category="fail", message="missing user source")
            return
        menu_options, menu_options_payload = self.request("GET", "/auth/api-keys/menu-options", **self.protected_auth())
        allowed_menus = []
        if menu_options.status_code == 200 and menu_options_payload.get("code") == 0:
            allowed_menus = [
                item.get("code")
                for item in menu_options_payload.get("data", [])
                if isinstance(item, dict) and item.get("code")
            ]
        action_options, action_options_payload = self.request("GET", "/auth/api-keys/action-options", **self.protected_auth())
        allowed_actions = ["VIEW", "CREATE", "EDIT", "DELETE", "EXPORT"]
        if action_options.status_code == 200 and action_options_payload.get("code") == 0:
            allowed_actions = [
                item.get("code")
                for item in action_options_payload.get("data", [])
                if isinstance(item, dict) and item.get("code")
            ] or allowed_actions
        response, payload = self.request(
            "POST",
            "/auth/api-keys",
            token=self.bootstrap_operator_token,
            params={"userId": target_user_id},
            body={
                "keyName": f"smoke-key-{SUFFIX}",
                "usageScope": "全部接口",
                "allowedMenus": allowed_menus,
                "allowedActions": allowed_actions,
                "expireDays": 7,
            },
            extra_headers={"X-TOTP-Code": self.totp(self.bootstrap_temp_secret)},
        )
        ok = response.status_code == 200 and payload.get("code") == 0
        self.add("/auth/api-keys", "POST", "/auth/api-keys", ok, category="write" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if not ok:
            return
        key_id = payload["data"]["id"]
        raw_key = payload["data"].get("rawKey")
        detail, detail_payload = self.request("GET", f"/auth/api-keys/{key_id}", token=self.bootstrap_operator_token)
        self.add("/auth/api-keys/{id}", "GET", f"/auth/api-keys/{key_id}", detail.status_code == 200 and detail_payload.get("code") == 0, category="read" if detail.status_code == 200 else "fail", status=detail.status_code, code=detail_payload.get("code") if detail_payload else None, message=detail_payload.get("message") if detail_payload else None)
        if raw_key:
            api_key_response = self.session.get(
                f"{BASE_URL}/materials",
                headers={"X-API-Key": raw_key},
                params={"page": 0, "size": 1},
                timeout=60,
            )
            api_key_payload = api_key_response.json()
            self.add("/materials (api-key)", "GET", "/materials", api_key_response.status_code == 200 and api_key_payload.get("code") == 0, category="read" if api_key_response.status_code == 200 else "fail", status=api_key_response.status_code, code=api_key_payload.get("code"), message=api_key_payload.get("message"))
        revoke, revoke_payload = self.request("POST", f"/auth/api-keys/{key_id}/revoke", token=self.bootstrap_operator_token)
        self.add("/auth/api-keys/{id}/revoke", "POST", f"/auth/api-keys/{key_id}/revoke", revoke.status_code == 200 and revoke_payload.get("code") == 0, category="write" if revoke.status_code == 200 else "fail", status=revoke.status_code, code=revoke_payload.get("code") if revoke_payload else None, message=revoke_payload.get("message") if revoke_payload else None)

    def ensure_sensitive_operator(self):
        if self.bootstrap_operator_token and self.bootstrap_temp_secret:
            return
        previous_auth_mode = self.auth_mode
        self.auth_mode = "jwt"
        self.prepare_api_key_mode()
        self.auth_mode = previous_auth_mode

    def build_general_setting_payload(self):
        return {
            "settingCode": f"SMOKE_NR_{SUFFIX}",
            "settingName": f"Smoke NoRule {SUFFIX}",
            "billName": "SmokeBill",
            "prefix": "SM",
            "dateRule": "yyyyMMdd",
            "serialLength": 4,
            "resetRule": "DAILY",
            "sampleNo": f"SM{SUFFIX}20260425-0001",
            "status": "正常",
            "remark": f"smoke-{SUFFIX}",
        }

    def build_company_setting_payload(self):
        return {
            "companyName": f"烟台星港钢铁 {SUFFIX[-6:]}",
            "taxNo": f"91370600{SUFFIX[-10:]}",
            "bankName": "中国银行烟台开发区支行",
            "bankAccount": f"62166123{SUFFIX[-10:]}",
            "taxRate": 0.13,
            "status": "正常",
            "remark": f"smoke-{SUFFIX}",
        }

    def build_settlement_account_payload(self, company_name=None):
        return {
            "accountName": f"结算账户{SUFFIX[-6:]}",
            "companyName": company_name or f"烟台星港钢铁 {SUFFIX[-6:]}",
            "bankName": "招商银行烟台分行",
            "bankAccount": f"62258888{SUFFIX[-10:]}",
            "usageType": "通用",
            "status": "正常",
            "remark": f"smoke-{SUFFIX}",
        }

    def build_invoice_receipt_payload(self, supplier_name=None, purchase_order=None):
        items = []
        source_purchase_order_nos = ""
        if purchase_order and isinstance(purchase_order.get("items"), list):
            source_purchase_order_nos = str(purchase_order.get("orderNo") or "")
            for item in purchase_order["items"]:
                items.append({
                    "sourceNo": source_purchase_order_nos,
                    "sourcePurchaseOrderItemId": item.get("id"),
                    "materialCode": item.get("materialCode"),
                    "brand": item.get("brand"),
                    "category": item.get("category"),
                    "material": item.get("material"),
                    "spec": item.get("spec"),
                    "length": item.get("length"),
                    "unit": item.get("unit"),
                    "warehouseName": item.get("warehouseName"),
                    "batchNo": item.get("batchNo"),
                    "quantity": item.get("quantity"),
                    "quantityUnit": item.get("quantityUnit"),
                    "pieceWeightTon": item.get("pieceWeightTon"),
                    "piecesPerBundle": item.get("piecesPerBundle"),
                    "weightTon": item.get("weightTon"),
                    "unitPrice": item.get("unitPrice"),
                    "amount": item.get("amount"),
                })
        return {
            "receiveNo": f"SP{SUFFIX[-10:]}",
            "invoiceNo": f"INV-R-{SUFFIX[-8:]}",
            "sourcePurchaseOrderNos": source_purchase_order_nos,
            "supplierName": supplier_name or f"供应商{SUFFIX[-6:]}",
            "invoiceTitle": supplier_name or f"供应商{SUFFIX[-6:]}",
            "invoiceDate": "2026-04-25",
            "invoiceType": "增值税专票",
            "amount": round(sum(float(item.get("amount") or 0) for item in items), 2) if items else 6666.00,
            "taxAmount": round((round(sum(float(item.get("amount") or 0) for item in items), 2) if items else 6666.00) * 0.13, 2),
            "status": "草稿",
            "operatorName": "财务主管-周敏",
            "remark": f"smoke-{SUFFIX}",
            "items": items or [{
                "sourceNo": f"PO{SUFFIX[-10:]}",
                "sourcePurchaseOrderItemId": None,
                "materialCode": f"MAT{SUFFIX[-6:]}",
                "brand": "宝钢",
                "category": "板材",
                "material": "Q235",
                "spec": "10mm",
                "length": "6000",
                "unit": "吨",
                "warehouseName": "默认仓",
                "batchNo": "",
                "quantity": 2,
                "quantityUnit": "件",
                "pieceWeightTon": 1.5,
                "piecesPerBundle": 0,
                "weightTon": 3.0,
                "unitPrice": 2222.0,
                "amount": 6666.0,
            }],
        }

    def build_invoice_issue_payload(self, customer_name=None, project_name=None, sales_order=None):
        items = []
        source_sales_order_nos = ""
        if sales_order and isinstance(sales_order.get("items"), list):
            source_sales_order_nos = str(sales_order.get("orderNo") or "")
            for item in sales_order["items"]:
                items.append({
                    "sourceNo": source_sales_order_nos,
                    "sourceSalesOrderItemId": item.get("id"),
                    "materialCode": item.get("materialCode"),
                    "brand": item.get("brand"),
                    "category": item.get("category"),
                    "material": item.get("material"),
                    "spec": item.get("spec"),
                    "length": item.get("length"),
                    "unit": item.get("unit"),
                    "warehouseName": item.get("warehouseName"),
                    "batchNo": item.get("batchNo"),
                    "quantity": item.get("quantity"),
                    "quantityUnit": item.get("quantityUnit"),
                    "pieceWeightTon": item.get("pieceWeightTon"),
                    "piecesPerBundle": item.get("piecesPerBundle"),
                    "weightTon": item.get("weightTon"),
                    "unitPrice": item.get("unitPrice"),
                    "amount": item.get("amount"),
                })
        if items:
            last_item = items[-1]
            max_amount = float(last_item.get("amount") or 0)
            unit_price = float(last_item.get("unitPrice") or 0)
            if max_amount > 0 and unit_price > 0:
                partial_amount = round(max_amount * 0.61, 2)
                last_item["amount"] = partial_amount
                last_item["weightTon"] = round(partial_amount / unit_price, 3)
        total_amount = round(sum(float(item.get("amount") or 0) for item in items), 2) if items else 8888.00
        return {
            "issueNo": f"KP{SUFFIX[-10:]}",
            "invoiceNo": f"INV-I-{SUFFIX[-8:]}",
            "sourceSalesOrderNos": source_sales_order_nos,
            "customerName": customer_name or f"客户{SUFFIX[-6:]}",
            "projectName": project_name or f"Smoke Project {SUFFIX[-6:]}",
            "invoiceDate": "2026-04-25",
            "invoiceType": "增值税专票",
            "amount": total_amount,
            "taxAmount": round(total_amount * 0.13, 2),
            "status": "草稿",
            "operatorName": "财务主管-周敏",
            "remark": f"smoke-{SUFFIX}",
            "items": items or [{
                "sourceNo": f"SO{SUFFIX[-10:]}",
                "sourceSalesOrderItemId": None,
                "materialCode": f"MAT{SUFFIX[-6:]}",
                "brand": "鞍钢",
                "category": "卷板",
                "material": "Q355",
                "spec": "12mm",
                "length": "9000",
                "unit": "吨",
                "warehouseName": "默认仓",
                "batchNo": "",
                "quantity": 4,
                "quantityUnit": "件",
                "pieceWeightTon": 2.0,
                "piecesPerBundle": 0,
                "weightTon": 8.0,
                "unitPrice": 1111.0,
                "amount": 8888.0,
            }],
        }

    def run_generated_data_smoke(self):
        material = self.create_and_update_record("/materials", self.build_simple_payload_from_sample("/materials", "MaterialRequest"))
        supplier = self.create_and_update_record("/suppliers", self.build_simple_payload_from_sample("/suppliers", "SupplierRequest"))
        customer = self.create_and_update_record("/customers", self.build_simple_payload_from_sample("/customers", "CustomerRequest"))
        carrier = self.create_and_update_record("/carriers", self.build_simple_payload_from_sample("/carriers", "CarrierRequest"))
        warehouse = self.create_and_update_record("/warehouses", self.build_simple_payload_from_sample("/warehouses", "WarehouseRequest"))
        company_setting = None
        response, payload = self.request("GET", "/company-settings/current", **self.protected_auth())
        if response.status_code == 200 and payload.get("code") == 0 and isinstance(payload.get("data"), dict):
            company_setting = payload["data"]
        self.create_and_update_record(
            "/settlement-accounts",
            self.build_settlement_account_payload(company_setting["companyName"] if company_setting else None),
        )

        purchase_order = None
        if material and supplier:
            payload = self.build_item_payload_from_sample("/purchase-orders", "PurchaseOrderRequest")
            if payload:
                payload["orderNo"] = f"PO{SUFFIX[-10:]}"
                payload["supplierName"] = supplier["supplierName"]
                for item in payload.get("items") or []:
                    self.apply_material_fields(item, material)
                    item["warehouseName"] = warehouse["warehouseName"] if warehouse else item.get("warehouseName")
                purchase_order = self.create_and_update_record("/purchase-orders", payload)

        purchase_inbound = None
        if material and supplier and warehouse:
            payload = self.build_item_payload_from_sample("/purchase-inbounds", "PurchaseInboundRequest")
            if payload:
                payload["inboundNo"] = f"PI{SUFFIX[-10:]}"
                payload["purchaseOrderNo"] = purchase_order["orderNo"] if purchase_order else payload.get("purchaseOrderNo")
                payload["supplierName"] = supplier["supplierName"]
                payload["warehouseName"] = warehouse["warehouseName"]
                for index, item in enumerate(payload.get("items") or [], start=1):
                    self.apply_material_fields(item, material)
                    item["warehouseName"] = warehouse["warehouseName"]
                    if "batchNo" in item:
                        item["batchNo"] = f"BATCH-{SUFFIX[-8:]}-{index:02d}"
                purchase_inbound = self.create_and_update_record("/purchase-inbounds", payload)

        sales_order = None
        if material and customer:
            payload = self.build_item_payload_from_sample("/sales-orders", "SalesOrderRequest")
            if payload:
                payload["orderNo"] = f"SO{SUFFIX[-10:]}"
                payload["purchaseInboundNo"] = purchase_inbound["inboundNo"] if purchase_inbound else payload.get("purchaseInboundNo")
                payload["customerName"] = customer["customerName"]
                payload["projectName"] = f"Smoke Project {SUFFIX[-6:]}"
                for item in payload.get("items") or []:
                    self.apply_material_fields(item, material)
                    item["warehouseName"] = purchase_inbound["warehouseName"] if purchase_inbound else warehouse["warehouseName"]
                sales_order = self.create_and_update_record("/sales-orders", payload)

        sales_outbound = None
        if material and customer and warehouse and sales_order:
            payload = self.build_item_payload_from_sample("/sales-outbounds", "SalesOutboundRequest")
            if payload:
                payload["outboundNo"] = f"OB{SUFFIX[-10:]}"
                payload["salesOrderNo"] = sales_order["orderNo"]
                payload["customerName"] = sales_order["customerName"]
                payload["projectName"] = sales_order["projectName"]
                payload["warehouseName"] = warehouse["warehouseName"]
                for index, item in enumerate(payload.get("items") or [], start=1):
                    self.apply_material_fields(item, material)
                    item["warehouseName"] = item.get("warehouseName") or sales_order.get("items", [{}])[0].get("warehouseName") or warehouse["warehouseName"]
                    if "batchNo" in item:
                        item["batchNo"] = f"BATCH-{SUFFIX[-8:]}-{index:02d}"
                sales_outbound = self.create_and_update_record("/sales-outbounds", payload)

        freight_bill = None
        if material and carrier and sales_outbound:
            payload = self.build_item_payload_from_sample("/freight-bills", "FreightBillRequest")
            if payload:
                payload["billNo"] = f"FB{SUFFIX[-10:]}"
                payload["outboundNo"] = sales_outbound["outboundNo"]
                payload["carrierName"] = carrier["carrierName"]
                payload["customerName"] = sales_outbound["customerName"]
                payload["projectName"] = sales_outbound["projectName"]
                for item in payload.get("items") or []:
                    self.apply_material_fields(item, material)
                    item["sourceNo"] = sales_outbound["outboundNo"]
                    item["customerName"] = sales_outbound["customerName"]
                    item["projectName"] = sales_outbound["projectName"]
                    item["warehouseName"] = sales_outbound["warehouseName"]
                freight_bill = self.create_and_update_record("/freight-bills", payload)

        if material and supplier:
            payload = self.build_item_payload_from_sample("/purchase-contracts", "PurchaseContractRequest")
            if payload:
                payload["contractNo"] = f"PC{SUFFIX[-10:]}"
                payload["supplierName"] = supplier["supplierName"]
                for item in payload.get("items") or []:
                    self.apply_material_fields(item, material)
                self.create_and_update_record("/purchase-contracts", payload)

        if material and customer and sales_order:
            payload = self.build_item_payload_from_sample("/sales-contracts", "SalesContractRequest")
            if payload:
                payload["contractNo"] = f"SC{SUFFIX[-10:]}"
                payload["customerName"] = customer["customerName"]
                payload["projectName"] = sales_order["projectName"]
                for item in payload.get("items") or []:
                    self.apply_material_fields(item, material)
                    item["warehouseName"] = sales_order.get("items", [{}])[0].get("warehouseName") or warehouse["warehouseName"]
                self.create_and_update_record("/sales-contracts", payload)

        if purchase_inbound and supplier:
            payload = self.build_simple_payload_from_sample("/supplier-statements", "SupplierStatementRequest")
            if payload:
                payload["statementNo"] = f"SS{SUFFIX[-10:]}"
                payload["sourceInboundNos"] = purchase_inbound["inboundNo"]
                payload["supplierName"] = supplier["supplierName"]
                payload["purchaseAmount"] = purchase_inbound["totalAmount"]
                payload["closingAmount"] = purchase_inbound["totalAmount"]
                payload["paymentAmount"] = 0
                for index, item in enumerate(payload.get("items") or [], start=1):
                    item["sourceNo"] = purchase_inbound["inboundNo"]
                    if "batchNo" in item:
                        item["batchNo"] = f"BATCH-{SUFFIX[-8:]}-{index:02d}"
                    if material:
                        self.apply_material_fields(item, material)
                self.create_and_update_record("/supplier-statements", payload)

        if sales_order and customer:
            payload = self.build_simple_payload_from_sample("/customer-statements", "CustomerStatementRequest")
            if payload:
                payload["statementNo"] = f"CS{SUFFIX[-10:]}"
                payload["sourceOrderNos"] = sales_order["orderNo"]
                payload["customerName"] = customer["customerName"]
                payload["projectName"] = sales_order["projectName"]
                payload["salesAmount"] = sales_order["totalAmount"]
                payload["closingAmount"] = sales_order["totalAmount"]
                payload["receiptAmount"] = 0
                for item in payload.get("items") or []:
                    item["sourceNo"] = sales_order["orderNo"]
                    if material:
                        self.apply_material_fields(item, material)
                self.create_and_update_record("/customer-statements", payload)

        if customer and sales_order:
            payload = self.build_simple_payload_from_sample("/receipts", "ReceiptRequest")
            if payload:
                payload["receiptNo"] = f"RC{SUFFIX[-10:]}"
                payload["customerName"] = customer["customerName"]
                payload["projectName"] = sales_order["projectName"]
                self.create_and_update_record("/receipts", payload)

        if supplier:
            payload = self.build_simple_payload_from_sample("/payments", "PaymentRequest")
            if payload:
                payload["paymentNo"] = f"PM{SUFFIX[-10:]}"
                payload["counterpartyName"] = supplier["supplierName"]
                self.create_and_update_record("/payments", payload)

        if supplier:
            self.create_and_update_record(
                "/invoice-receipts",
                self.build_invoice_receipt_payload(supplier["supplierName"], purchase_order),
            )

        if customer:
            self.create_and_update_record(
                "/invoice-issues",
                self.build_invoice_issue_payload(
                    customer["customerName"],
                    sales_order["projectName"] if sales_order else None,
                    sales_order,
                ),
            )

        if freight_bill:
            item = copy.deepcopy(freight_bill["items"][0])
            item.pop("id", None)
            item.pop("lineNo", None)
            payload = {
                "statementNo": f"FS{SUFFIX[-10:]}",
                "sourceBillNos": freight_bill["billNo"],
                "carrierName": freight_bill["carrierName"],
                "startDate": freight_bill["billTime"],
                "endDate": freight_bill["billTime"],
                "totalWeight": freight_bill["totalWeight"],
                "totalFreight": freight_bill["totalFreight"],
                "paidAmount": 0,
                "unpaidAmount": freight_bill["totalFreight"],
                "status": "待确认",
                "signStatus": "未签署",
                "attachment": "",
                "attachmentIds": [],
                "remark": f"smoke-{SUFFIX}",
                "items": [item],
            }
            self.create_and_update_record("/freight-statements", payload)

        self.create_and_update_record("/general-settings", self.build_general_setting_payload())

    def run_role_action_smoke(self):
        role = self.first_records.get("/role-settings")
        if not role or not role.get("id"):
            return
        response, payload = self.request("GET", f"/role-settings/{role['id']}/actions", **self.protected_auth())
        if response.status_code == 200 and payload.get("code") == 0:
            update, update_payload = self.request("PUT", f"/role-settings/{role['id']}/actions", body=payload["data"], **self.protected_auth())
            self.add("/role-settings/{id}/actions", "PUT", f"/role-settings/{role['id']}/actions", update.status_code == 200 and update_payload.get("code") == 0, category="write" if update.status_code == 200 else "fail", status=update.status_code, code=update_payload.get("code") if update_payload else None, message=update_payload.get("message") if update_payload else None)

    def run_simple_crud_smoke(self):
        for path, schema_name in CRUD_SIMPLE.items():
            if path in self.generated:
                continue
            source = self.first_records.get(path)
            if path == "/general-settings":
                payload = self.build_general_setting_payload()
            elif not source:
                self.add(path, "POST", path, False, category="fail", message="missing source record")
                continue
            else:
                payload = self.mutate(self.pick(source, {"$ref": f"#/components/schemas/{schema_name}"}) or {}, path)
            response, body = self.request("POST", path, body=payload, **self.protected_auth())
            ok = response.status_code == 200 and body.get("code") == 0
            self.add(path, "POST", path, ok, category="write" if ok else "fail", status=response.status_code, code=body.get("code") if body else None, message=body.get("message") if body else None)
            if ok and isinstance(body.get("data"), dict) and body["data"].get("id") is not None:
                item_id = body["data"]["id"]
                self.created.append((path, item_id))
                update_payload = copy.deepcopy(payload)
                if "remark" in update_payload:
                    update_payload["remark"] = f"updated-{SUFFIX}"
                update, update_body = self.request("PUT", f"{path}/{item_id}", body=update_payload, **self.protected_auth())
                self.add(path, "PUT", f"{path}/{item_id}", update.status_code == 200 and update_body.get("code") == 0, category="write" if update.status_code == 200 else "fail", status=update.status_code, code=update_body.get("code") if update_body else None, message=update_body.get("message") if update_body else None)

    def run_item_crud_smoke(self):
        for path, schema_name in CRUD_WITH_ITEMS.items():
            if path in self.generated:
                continue
            source = self.first_records.get(path)
            if not source or not source.get("id"):
                self.add(path, "POST", path, False, category="fail", message="missing source record")
                continue
            detail, detail_payload = self.request("GET", f"{path}/{source['id']}", **self.protected_auth())
            if detail.status_code != 200 or detail_payload.get("code") != 0:
                self.add(path, "POST", path, False, category="fail", message="missing detail payload")
                continue
            payload = self.strip_item_ids(self.mutate(self.pick(detail_payload["data"], {"$ref": f"#/components/schemas/{schema_name}"}) or {}, path))
            response, body = self.request("POST", path, body=payload, **self.protected_auth())
            ok = response.status_code == 200 and body.get("code") == 0
            self.add(path, "POST", path, ok, category="write" if ok else "fail", status=response.status_code, code=body.get("code") if body else None, message=body.get("message") if body else None)
            if ok and isinstance(body.get("data"), dict) and body["data"].get("id") is not None:
                item_id = body["data"]["id"]
                self.created.append((path, item_id))
                update_payload = copy.deepcopy(payload)
                if "remark" in update_payload:
                    update_payload["remark"] = f"updated-{SUFFIX}"
                update, update_body = self.request("PUT", f"{path}/{item_id}", body=update_payload, **self.protected_auth())
                self.add(path, "PUT", f"{path}/{item_id}", update.status_code == 200 and update_body.get("code") == 0, category="write" if update.status_code == 200 else "fail", status=update.status_code, code=update_body.get("code") if update_body else None, message=update_body.get("message") if update_body else None)

    def run_freight_statement_smoke(self):
        response, payload = self.request("GET", "/freight-bills", params={"page": 0, "size": 1}, **self.protected_auth())
        if response.status_code != 200 or payload.get("code") != 0:
            self.add("/freight-statements", "POST", "/freight-statements", False, category="fail", message="missing freight bill source")
            return
        bill_id = payload["data"]["records"][0]["id"]
        detail, detail_payload = self.request("GET", f"/freight-bills/{bill_id}", **self.protected_auth())
        if detail.status_code != 200 or detail_payload.get("code") != 0:
            self.add("/freight-statements", "POST", "/freight-statements", False, category="fail", message="missing freight bill detail")
            return
        bill = detail_payload["data"]
        item = copy.deepcopy(bill["items"][0])
        item.pop("id", None)
        item.pop("lineNo", None)
        create_payload = {
            "statementNo": f"SMOKEFS{SUFFIX}",
            "sourceBillNos": bill["billNo"],
            "carrierName": bill["carrierName"],
            "startDate": bill["billTime"],
            "endDate": bill["billTime"],
            "totalWeight": bill["totalWeight"],
            "totalFreight": bill["totalFreight"],
            "paidAmount": 0,
            "unpaidAmount": bill["totalFreight"],
            "status": "待确认",
            "signStatus": "未签署",
            "attachment": "",
            "attachmentIds": [],
            "remark": f"smoke-{SUFFIX}",
            "items": [item],
        }
        response, payload = self.request("POST", "/freight-statements", body=create_payload, **self.protected_auth())
        ok = response.status_code == 200 and payload.get("code") == 0
        self.add("/freight-statements", "POST", "/freight-statements", ok, category="write" if ok else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)
        if ok:
            item_id = payload["data"]["id"]
            self.created.append(("/freight-statements", item_id))
            create_payload["remark"] = f"updated-{SUFFIX}"
            update, update_payload = self.request("PUT", f"/freight-statements/{item_id}", body=create_payload, **self.protected_auth())
            self.add("/freight-statements", "PUT", f"/freight-statements/{item_id}", update.status_code == 200 and update_payload.get("code") == 0, category="write" if update.status_code == 200 else "fail", status=update.status_code, code=update_payload.get("code") if update_payload else None, message=update_payload.get("message") if update_payload else None)

    def run_user_account_smoke(self):
        role_response, role_payload = self.request("GET", "/role-settings", params={"page": 0, "size": 1}, **self.protected_auth())
        if role_response.status_code != 200 or role_payload.get("code") != 0:
            self.add("/user-accounts", "POST", "/user-accounts", False, category="fail", message="missing role source")
            return
        role_name = role_payload["data"]["records"][0]["roleName"]
        payload = {
            "loginName": f"smoke_{SUFFIX}",
            "userName": f"Smoke User {SUFFIX}",
            "mobile": "1" + SUFFIX.zfill(10)[:10],
            "roleNames": [role_name],
            "dataScope": "全部",
            "permissionSummary": "",
            "status": "正常",
            "remark": f"smoke-{SUFFIX}",
        }
        response, body = self.request("POST", "/user-accounts", body=payload, **self.protected_auth())
        ok = response.status_code == 200 and body.get("code") == 0
        self.add("/user-accounts", "POST", "/user-accounts", ok, category="write" if ok else "fail", status=response.status_code, code=body.get("code") if body else None, message=body.get("message") if body else None)
        if not ok:
            return
        user_id = body["data"]["id"]
        self.created.append(("/user-accounts", user_id))

        setup, setup_payload = self.request("POST", f"/user-accounts/{user_id}/2fa/setup", **self.protected_auth())
        setup_ok = setup.status_code == 200 and setup_payload.get("code") == 0
        self.add("/user-accounts/{id}/2fa/setup", "POST", f"/user-accounts/{user_id}/2fa/setup", setup_ok, category="write" if setup_ok else "fail", status=setup.status_code, code=setup_payload.get("code") if setup_payload else None, message=setup_payload.get("message") if setup_payload else None)
        if setup_ok:
            secret = setup_payload["data"]["secret"]
            code = self.totp(secret)
            enable, enable_payload = self.request("POST", f"/user-accounts/{user_id}/2fa/enable", body={"totpCode": code}, **self.protected_auth())
            enable_ok = enable.status_code == 200 and enable_payload.get("code") == 0
            self.add("/user-accounts/{id}/2fa/enable", "POST", f"/user-accounts/{user_id}/2fa/enable", enable_ok, category="write" if enable_ok else "fail", status=enable.status_code, code=enable_payload.get("code") if enable_payload else None, message=enable_payload.get("message") if enable_payload else None)

            disable, disable_payload = self.request("POST", f"/user-accounts/{user_id}/2fa/disable", **self.protected_auth())
            disable_ok = disable.status_code == 200 and disable_payload.get("code") == 0
            self.add("/user-accounts/{id}/2fa/disable", "POST", f"/user-accounts/{user_id}/2fa/disable", disable_ok, category="write" if disable_ok else "fail", status=disable.status_code, code=disable_payload.get("code") if disable_payload else None, message=disable_payload.get("message") if disable_payload else None)

    def run_cleanup(self):
        for path, item_id in reversed(self.created):
            response, payload = self.request("DELETE", f"{path}/{item_id}", **self.protected_auth())
            self.add(path, "DELETE", f"{path}/{item_id}", response.status_code == 200 and payload.get("code") == 0, category="write" if response.status_code == 200 else "fail", status=response.status_code, code=payload.get("code") if payload else None, message=payload.get("message") if payload else None)

    def run_refresh_revoke_smoke(self):
        response, payload = self.login(user_agent=f"smoke-revoke-{SUFFIX}")
        if response.status_code != 200 or payload.get("code") != 0:
            self.add("/auth/refresh-tokens/{id}/revoke", "POST", "/auth/refresh-tokens/{id}/revoke", False, category="fail", message="seed login failed")
            return
        token_page, token_payload = self.request("GET", "/auth/refresh-tokens", params={"page": 0, "size": 100}, **self.protected_auth())
        target_id = None
        if token_page.status_code == 200 and token_payload.get("code") == 0:
            for row in token_payload["data"].get("records") or []:
                if f"smoke-revoke-{SUFFIX}" in str(row.get("deviceInfo") or "") and row.get("status") == "有效":
                    target_id = row["id"]
                    break
        if not target_id:
            self.add("/auth/refresh-tokens/{id}/revoke", "POST", "/auth/refresh-tokens/{id}/revoke", False, category="fail", message="missing active refresh token")
            return
        revoke, revoke_payload = self.request("POST", f"/auth/refresh-tokens/{target_id}/revoke", **self.protected_auth())
        self.add("/auth/refresh-tokens/{id}/revoke", "POST", f"/auth/refresh-tokens/{target_id}/revoke", revoke.status_code == 200 and revoke_payload.get("code") == 0, category="write" if revoke.status_code == 200 else "fail", status=revoke.status_code, code=revoke_payload.get("code") if revoke_payload else None, message=revoke_payload.get("message") if revoke_payload else None)

    def run_logout_smoke(self):
        response, payload = self.login(user_agent=f"smoke-logout-{SUFFIX}")
        logout = requests.post(f"{BASE_URL}/auth/logout", json={"refreshToken": payload["data"]["refreshToken"]}, timeout=60)
        logout_payload = logout.json()
        self.add("/auth/logout", "POST", "/auth/logout", logout.status_code == 200 and logout_payload.get("code") == 0, category="auth", status=logout.status_code, code=logout_payload.get("code"), message=logout_payload.get("message"))

    def totp(self, secret, interval=30, digits=6):
        key = base64.b32decode(secret.upper() + "=" * ((8 - len(secret) % 8) % 8))
        counter = int(time.time()) // interval
        msg = struct.pack(">Q", counter)
        digest = hmac.new(key, msg, hashlib.sha1).digest()
        offset = digest[-1] & 0x0F
        code = (struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % (10 ** digits)
        return str(code).zfill(digits)

    def summary(self):
        return {
            "total": len(self.results),
            "passed": sum(1 for item in self.results if item["ok"]),
            "failed": sum(1 for item in self.results if not item["ok"]),
            "failures": [item for item in self.results if not item["ok"]],
            "skipped": SKIPPED,
        }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--auth-mode", choices=["jwt", "apikey"], default="jwt")
    args = parser.parse_args()
    runner = SmokeRunner(auth_mode=args.auth_mode)
    summary = runner.run()
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if not summary["failures"] else 1


if __name__ == "__main__":
    sys.exit(main())
