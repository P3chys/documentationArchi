#!/usr/bin/env python3
"""Uploads an export from documentationArchi to Confluence Data Center or Server. Python 3.8+, no packages needed.

    CONFLUENCE_TOKEN=<personal access token> python upload_to_confluence.py \
        --url https://confluence.example.com --space ARCH [--parent "Existing page"]

Uses CONFLUENCE_USER and CONFLUENCE_PASSWORD instead when no token is set. Pages are matched by title in the space:
existing ones are updated (a new page version), the others created, so the same export can be uploaded again.
"""
import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path


class Confluence:
    def __init__(self, url, space):
        self.api = url.rstrip("/") + "/rest/api"
        self.space = space
        token = os.environ.get("CONFLUENCE_TOKEN")
        user, password = os.environ.get("CONFLUENCE_USER"), os.environ.get("CONFLUENCE_PASSWORD")
        if token:
            self.auth = "Bearer " + token
        elif user and password:
            self.auth = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()
        else:
            sys.exit("Set CONFLUENCE_TOKEN, or CONFLUENCE_USER and CONFLUENCE_PASSWORD")

    def call(self, method, path, body=None, headers=None):
        request = urllib.request.Request(self.api + path, data=body, method=method)
        request.add_header("Authorization", self.auth)
        request.add_header("Accept", "application/json")
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        try:
            with urllib.request.urlopen(request) as response:
                return json.load(response)
        except urllib.error.HTTPError as e:
            sys.exit(f"{method} {path} failed: {e.code} {e.read().decode(errors='replace')[:500]}")

    def send_json(self, method, path, data):
        return self.call(method, path, json.dumps(data).encode(), {"Content-Type": "application/json"})

    def find(self, title):
        query = urllib.parse.urlencode({"title": title, "spaceKey": self.space, "expand": "version"})
        results = self.call("GET", "/content?" + query)["results"]
        return results[0] if results else None

    def ensure_page(self, title, parent_id):
        page = self.find(title)
        if page:
            return page
        data = {"type": "page", "title": title, "space": {"key": self.space},
                "body": {"storage": {"value": "", "representation": "storage"}}}
        if parent_id:
            data["ancestors"] = [{"id": parent_id}]
        page = self.send_json("POST", "/content", data)
        page.setdefault("version", {"number": 1})
        return page

    def update_page(self, page, title, body, parent_id):
        data = {"type": "page", "title": title, "version": {"number": page["version"]["number"] + 1},
                "body": {"storage": {"value": body, "representation": "storage"}}}
        if parent_id:
            data["ancestors"] = [{"id": parent_id}]
        self.send_json("PUT", f"/content/{page['id']}", data)

    def attach(self, page_id, file):
        boundary = uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file.name}"\r\n'
                f"Content-Type: image/png\r\n\r\n").encode() + file.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        headers = {"Content-Type": f"multipart/form-data; boundary={boundary}", "X-Atlassian-Token": "nocheck"}
        query = urllib.parse.urlencode({"filename": file.name})
        existing = self.call("GET", f"/content/{page_id}/child/attachment?{query}")["results"]
        path = f"/content/{page_id}/child/attachment"
        self.call("POST", path + (f"/{existing[0]['id']}/data" if existing else ""), body, headers)


def main():
    parser = argparse.ArgumentParser(description="Upload a documentationArchi export to Confluence")
    parser.add_argument("--url", required=True, help="Confluence base URL")
    parser.add_argument("--space", required=True, help="space key")
    parser.add_argument("--parent", help="title of the existing page to put the export under")
    args = parser.parse_args()

    folder = Path(__file__).resolve().parent
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    confluence = Confluence(args.url, args.space)

    top_parent = None
    if args.parent:
        parent = confluence.find(args.parent)
        if not parent:
            sys.exit(f'Parent page "{args.parent}" not found in space {args.space}')
        top_parent = parent["id"]

    # every page exists before any content goes in, so that links between pages resolve
    ids, pages = {}, {}
    for entry in manifest["pages"]:
        parent_id = ids[entry["parent"]] if entry["parent"] else top_parent
        page = confluence.ensure_page(entry["title"], parent_id)
        ids[entry["title"]], pages[entry["title"]] = page["id"], page

    for entry in manifest["pages"]:
        title = entry["title"]
        for name in entry["attachments"]:
            confluence.attach(ids[title], folder / "attachments" / name)
        parent_id = ids[entry["parent"]] if entry["parent"] else top_parent
        body = (folder / entry["file"]).read_text(encoding="utf-8")
        confluence.update_page(pages[title], title, body, parent_id)
        print("Uploaded", title)


if __name__ == "__main__":
    main()
