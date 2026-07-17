#!/usr/bin/env python3
"""JWT-only read smoke checks for the Leo ERP development API."""

import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass
from typing import Any

import requests


DEFAULT_BASE_URL = "http://127.0.0.1:11211/api"
DEFAULT_LOGIN_NAME = "admin"
DEFAULT_TIMEOUT = 30

RETIRED_RESOURCES = {
    "api-key",
    "rate-limit",
    "security-key",
    "session",
    "upload-rule",
    "no-rule",
}

RETIRED_ENDPOINTS = (
    ("GET", "/auth/api-keys"),
    ("GET", "/auth/refresh-tokens"),
    ("POST", "/auth/login-2fa"),
    ("GET", "/system/security-keys"),
    ("GET", "/rate-limit-rules"),
    ("GET", "/upload-rules/page"),
    ("GET", "/number-rules"),
    ("GET", "/no-rules"),
    ("GET", "/general-settings/upload-rule"),
)


@dataclass(frozen=True)
class CheckResult:
    name: str
    ok: bool
    status: int | None = None
    detail: str | None = None


class SmokeRunner:
    def __init__(
        self,
        base_url: str,
        login_name: str,
        password: str,
        timeout: int,
    ) -> None:
        if not password:
            raise ValueError("缺少登录密码，请设置 LEO_SMOKE_PASSWORD 或传入 --password")
        self.base_url = base_url.rstrip("/")
        self.login_name = login_name
        self.password = password
        self.timeout = timeout
        self.session = requests.Session()
        self.access_token = ""
        self.results: list[CheckResult] = []

    def run(self) -> dict[str, Any]:
        self.check_health()
        self.login()
        if self.access_token:
            self.check_general_settings()
            self.check_retired_endpoints()
            self.logout()
        return self.summary()

    def check_health(self) -> None:
        response = self.session.get(
            f"{self.base_url}/health",
            timeout=self.timeout,
        )
        self.add("health", response.status_code == 200, response)

    def login(self) -> None:
        response = self.session.post(
            f"{self.base_url}/auth/login",
            json={"loginName": self.login_name, "password": self.password},
            timeout=self.timeout,
        )
        payload = self.parse_json(response)
        data = payload.get("data") if isinstance(payload, dict) else None
        self.access_token = data.get("accessToken", "") if isinstance(data, dict) else ""
        user = data.get("user") if isinstance(data, dict) else None
        permissions = user.get("permissions", []) if isinstance(user, dict) else []
        resources = {
            item.get("resource")
            for item in permissions
            if isinstance(item, dict) and isinstance(item.get("resource"), str)
        }
        retired_resources = sorted(RETIRED_RESOURCES & resources)
        ok = (
            response.status_code == 200
            and payload.get("code") == 0
            and bool(self.access_token)
            and not retired_resources
        )
        detail = payload.get("message")
        if retired_resources:
            detail = f"登录权限仍包含退役资源: {retired_resources}"
        self.add(
            "jwt-login-and-permissions",
            ok,
            response,
            detail,
        )

    def check_general_settings(self) -> None:
        response = self.authorized_request(
            "GET",
            "/general-settings",
            params={"page": 0, "size": 5},
        )
        payload = self.parse_json(response)
        ok = response.status_code == 200 and payload.get("code") == 0
        detail = payload.get("message")
        if ok:
            rows = self.page_rows(payload.get("data"))
            if rows:
                keys = set(rows[0])
                legacy_fields = {"billName", "sampleNo"} & keys
                ok = not legacy_fields and {"settingGroup", "settingValue"} <= keys
                if not ok:
                    detail = f"通用设置字段契约异常: {sorted(keys)}"
        self.add("general-settings-contract", ok, response, detail)

    def check_retired_endpoints(self) -> None:
        for method, path in RETIRED_ENDPOINTS:
            response = self.authorized_request(method, path)
            ok = response.status_code in {404, 405}
            self.add(f"retired:{path}", ok, response)

    def logout(self) -> None:
        response = self.authorized_request("POST", "/auth/logout", json={})
        payload = self.parse_json(response)
        self.add(
            "logout",
            response.status_code == 200 and payload.get("code") == 0,
            response,
            payload.get("message"),
        )

    def authorized_request(
        self,
        method: str,
        path: str,
        **kwargs: Any,
    ) -> requests.Response:
        headers = dict(kwargs.pop("headers", {}))
        headers["Authorization"] = f"Bearer {self.access_token}"
        return self.session.request(
            method,
            f"{self.base_url}{path}",
            headers=headers,
            timeout=self.timeout,
            **kwargs,
        )

    def add(
        self,
        name: str,
        ok: bool,
        response: requests.Response,
        detail: str | None = None,
    ) -> None:
        self.results.append(
            CheckResult(
                name=name,
                ok=ok,
                status=response.status_code,
                detail=detail,
            )
        )

    @staticmethod
    def parse_json(response: requests.Response) -> dict[str, Any]:
        try:
            payload = response.json()
        except requests.JSONDecodeError:
            return {}
        return payload if isinstance(payload, dict) else {}

    @staticmethod
    def page_rows(data: Any) -> list[dict[str, Any]]:
        if not isinstance(data, dict):
            return []
        rows = data.get("content")
        if not isinstance(rows, list):
            return []
        return [row for row in rows if isinstance(row, dict)]

    def summary(self) -> dict[str, Any]:
        failures = [result for result in self.results if not result.ok]
        return {
            "total": len(self.results),
            "passed": len(self.results) - len(failures),
            "failed": len(failures),
            "results": [asdict(result) for result in self.results],
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base-url",
        default=os.environ.get("LEO_SMOKE_BASE_URL", DEFAULT_BASE_URL),
    )
    parser.add_argument(
        "--login-name",
        default=os.environ.get("LEO_SMOKE_LOGIN_NAME", DEFAULT_LOGIN_NAME),
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("LEO_SMOKE_PASSWORD", ""),
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        runner = SmokeRunner(
            base_url=args.base_url,
            login_name=args.login_name,
            password=args.password,
            timeout=args.timeout,
        )
        summary = runner.run()
    except (requests.RequestException, ValueError) as exc:
        print(json.dumps({"failed": 1, "error": str(exc)}, ensure_ascii=False))
        return 1
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
