"""Đẩy backlog.py lên GitHub: label, milestone theo sprint, issue US -> sub-issue Task, Project board.

Chạy lại an toàn: issue đã có (khớp theo tiền tố [US-xx] / [T-xx.n] trong tiêu đề) sẽ bỏ qua.
Yêu cầu: gh CLI đã đăng nhập với scope `project`.

    python tools/tracking/github_sync.py --dry-run
    python tools/tracking/github_sync.py
"""

import argparse
import json
import subprocess
import sys
import time

from backlog import BACKLOG, EPICS, SPRINT_GOALS, TASK_TYPES, sprint_dates

REPO = "FresherTeamOmiAI/OmniAI"  # repo đã chuyển sang organization
# Project board "OmniAI" vẫn thuộc tài khoản cá nhân, giữ owner cũ để không tạo board trùng
OWNER = "dangquochung1"
PROJECT_TITLE = "OmniAI"

GITHUB_USER = {
    "HungDQ": "dangquochung1",
    "ChienPX": "chienapc",
    "TuTM": "trolling1349765",
    "LucVT": "Yin2606",
}

STATUS_OPTIONS = [("Todo", "GRAY"), ("In Progress", "YELLOW"), ("In Review", "PURPLE"), ("Done", "GREEN")]

DRY_RUN = False


def gh(*args, input=None, parse=True):
    if DRY_RUN and args[0] != "api" and args[:2] not in (("project", "list"), ("project", "field-list")):
        print("  [dry-run] gh", " ".join(args))
        return None
    for attempt in range(4):  # GraphQL của Projects thỉnh thoảng lỗi tạm thời
        proc = subprocess.run(["gh", *args], input=json.dumps(input).encode("utf-8") if input is not None else None,
                              capture_output=True)
        if proc.returncode == 0 or b"Something went wrong" not in proc.stderr:
            break
        time.sleep(5 * (attempt + 1))
    if proc.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)}\n{proc.stderr.decode('utf-8', 'replace')}")
    out = proc.stdout.decode("utf-8")
    return json.loads(out) if parse and out.strip() else out


def api(method, path, body=None):
    if DRY_RUN and method != "GET":
        print(f"  [dry-run] {method} {path} {(body or {}).get('title', '')}")
        return {"number": 0, "id": 0, "node_id": ""}
    args = ["api", "-X", method, path]
    if body is not None:
        args += ["--input", "-"]
    result = gh(*args, input=body)
    if method != "GET":
        time.sleep(1)  # tránh secondary rate limit khi tạo nhiều nội dung
    return result


def paged(path):
    items, page = [], 1
    while True:
        batch = api("GET", f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
        items += batch
        if len(batch) < 100:
            return items
        page += 1


# ---------------- labels & milestones ----------------

def labels_wanted():
    wanted = {"type:story": ("5319e7", "User Story"), "type:task": ("c5def5", "Task thuộc một User Story")}
    for key, name in EPICS.items():
        wanted[f"epic:{key}"] = ("fbca04", name)
    wanted.update({"prio:P0": ("b60205", "Must have"), "prio:P1": ("d93f0b", "Should have"),
                   "prio:P2": ("fef2c0", "Could have")})
    for t in TASK_TYPES:
        wanted[f"kind:{t}"] = ("bfdadc", f"Loại task {t}")
    for story in BACKLOG:
        wanted.setdefault(f"module:{story['module']}", ("0e8a16", f"Module {story['module']}"))
    return wanted


def sync_labels():
    existing = {l["name"] for l in paged(f"repos/{REPO}/labels")}
    for name, (color, desc) in labels_wanted().items():
        if name not in existing:
            print(f"label  + {name}")
            api("POST", f"repos/{REPO}/labels", {"name": name, "color": color, "description": desc})


def sync_milestones():
    existing = {m["title"]: m["number"] for m in paged(f"repos/{REPO}/milestones?state=all")}
    result = {}
    for n, goal in enumerate(SPRINT_GOALS):
        title = f"Sprint {n}"
        if title not in existing:
            start, end = sprint_dates(n)
            print(f"milestone + {title}")
            m = api("POST", f"repos/{REPO}/milestones", {
                "title": title, "description": f"{start:%d/%m/%Y} - {end:%d/%m/%Y}: {goal}",
                "due_on": f"{end:%Y-%m-%d}T23:59:59Z"})
            existing[title] = m["number"]
        result[n] = existing[title]
    return result


# ---------------- issues ----------------

def us_body(story):
    rows = "\n".join(f"- [ ] T-{story['id'][3:]}.{i} {t['title']} (@{GITHUB_USER[t['assignee']]}, {t['est']} md)"
                     for i, t in enumerate(story["tasks"], start=1))
    return (f"**User Story:** {story['story']}\n\n"
            f"### Acceptance Criteria\n```gherkin\n{story['ac']}\n```\n\n"
            f"| Epic | MoSCoW | Priority | Module | Channel | Owner |\n|---|---|---|---|---|---|\n"
            f"| {story['epic']} - {EPICS[story['epic']]} | {story['moscow']} | {story['priority']} | "
            f"{story['module']} | {story['channel']} | @{GITHUB_USER[story['owner']]} |\n\n"
            f"### Tasks\n{rows}\n\n"
            f"_Sinh từ tools/tracking/backlog.py. PO nghiệm thu US khi mọi AC pass._")


def task_body(story, task_id, task):
    return (f"Thuộc **{story['id']} {story['title']}**\n\n"
            f"| Loại | Estimate | Assignee |\n|---|---|---|\n"
            f"| {task['type']} | {task['est']} md | @{GITHUB_USER[task['assignee']]} |\n\n"
            f"### Tiêu chí hoàn thành / test\n{task['test']}\n\n"
            f"Commit/PR ghi mã `[{task_id}]`; PR ghi `Closes #<số issue này>` để tự đóng.")


def ensure_issue(existing, key, title, body, labels, assignee, milestone):
    if key in existing:
        return existing[key]
    print(f"issue  + {title}")
    issue = api("POST", f"repos/{REPO}/issues", {"title": title, "body": body, "labels": labels,
                                                 "assignees": [assignee], "milestone": milestone})
    existing[key] = issue
    return issue


def sync_issues(milestones):
    existing = {}
    for issue in paged(f"repos/{REPO}/issues?state=all"):
        if "pull_request" not in issue and issue["title"].startswith("[") and "]" in issue["title"]:
            existing[issue["title"][1:issue["title"].index("]")]] = issue

    created = []  # (issue, estimate or None)
    for story in BACKLOG:
        labels = ["type:story", f"epic:{story['epic']}", f"prio:{story['priority']}", f"module:{story['module']}"]
        us_issue = ensure_issue(existing, story["id"], f"[{story['id']}] {story['title']}", us_body(story),
                                labels, GITHUB_USER[story["owner"]], milestones[story["sprint"]])
        created.append((us_issue, None))

        current_subs = set()
        if not DRY_RUN and us_issue["number"]:
            current_subs = {s["id"] for s in api("GET", f"repos/{REPO}/issues/{us_issue['number']}/sub_issues")}

        for i, task in enumerate(story["tasks"], start=1):
            task_id = f"T-{story['id'][3:]}.{i}"
            t_labels = ["type:task", f"kind:{task['type']}", f"prio:{story['priority']}", f"module:{story['module']}"]
            t_issue = ensure_issue(existing, task_id, f"[{task_id}] {task['title']}", task_body(story, task_id, task),
                                   t_labels, GITHUB_USER[task["assignee"]], milestones[story["sprint"]])
            created.append((t_issue, task["est"]))
            if t_issue["id"] not in current_subs:
                api("POST", f"repos/{REPO}/issues/{us_issue['number']}/sub_issues", {"sub_issue_id": t_issue["id"]})
    return created


# ---------------- project ----------------

def sync_project(items):
    projects = gh("project", "list", "--owner", OWNER, "--format", "json")["projects"]
    project = next((p for p in projects if p["title"] == PROJECT_TITLE), None)
    if project is None:
        print(f"project + {PROJECT_TITLE}")
        if DRY_RUN:
            return
        project = gh("project", "create", "--owner", OWNER, "--title", PROJECT_TITLE, "--format", "json")
        gh("project", "link", str(project["number"]), "--owner", OWNER, "--repo", REPO, parse=False)
    number, project_id = str(project["number"]), project["id"]

    fields = {f["name"]: f for f in gh("project", "field-list", number, "--owner", OWNER, "--format", "json")["fields"]}
    for name in ("Estimate (md)", "Actual (md)"):
        if name not in fields:
            print(f"field  + {name}")
            fields[name] = gh("project", "field-create", number, "--owner", OWNER, "--name", name,
                              "--data-type", "NUMBER", "--format", "json")
    status = fields["Status"]
    if [o["name"] for o in status.get("options", [])] != [s[0] for s in STATUS_OPTIONS]:
        print("field  ~ Status: " + " / ".join(s[0] for s in STATUS_OPTIONS))
        options = ",".join(f'{{name:"{n}",color:{c},description:""}}' for n, c in STATUS_OPTIONS)
        gh("api", "graphql", "-f", "query=mutation { updateProjectV2Field(input:{fieldId:\"%s\", "
                                   "singleSelectOptions:[%s]}) { projectV2Field { ... on ProjectV2SingleSelectField { id } } } }"
           % (status["id"], options))
    if DRY_RUN:
        return

    def board_items():
        return {i["content"].get("url"): i for i in
                gh("project", "item-list", number, "--owner", OWNER, "--limit", "1000", "--format", "json")["items"]}

    in_project = board_items()
    for issue, _ in items:
        if issue["html_url"] in in_project:
            continue
        print(f"board  + {issue['title']}")
        # thêm theo node_id: `gh project item-add --url` đôi khi báo "resource not found" với issue mới tạo
        try:
            gh("api", "graphql", "-f", "query=mutation { addProjectV2ItemById(input:{projectId:\"%s\", "
                                       "contentId:\"%s\"}) { item { id } } }" % (project_id, issue["node_id"]))
        except RuntimeError as e:
            if "already exists" not in str(e):  # GitHub đôi khi đã thêm nhưng item-list chưa thấy
                raise
        time.sleep(1)

    status = next(f for f in gh("project", "field-list", number, "--owner", OWNER, "--format", "json")["fields"]
                  if f["name"] == "Status")
    todo_id = next(o["id"] for o in status["options"] if o["name"] == "Todo")
    in_project = board_items()
    for issue, estimate in items:
        item = in_project.get(issue["html_url"])
        if item is not None and not item.get("status"):
            print(f"status Todo -> {issue['title']}")
            gh("project", "item-edit", "--id", item["id"], "--project-id", project_id,
               "--field-id", status["id"], "--single-select-option-id", todo_id, parse=False)
        if estimate is not None and item is not None and item.get("estimate (md)") is None:
            print(f"estimate {estimate} -> {issue['title']}")
            gh("project", "item-edit", "--id", item["id"], "--project-id", project_id,
               "--field-id", fields["Estimate (md)"]["id"], "--number", str(estimate), parse=False)
    print(f"Project: https://github.com/users/{OWNER}/projects/{number}")


def main():
    global DRY_RUN
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="chỉ in ra, không tạo gì trên GitHub")
    DRY_RUN = parser.parse_args().dry_run
    sys.stdout.reconfigure(encoding="utf-8")

    sync_labels()
    milestones = sync_milestones()
    items = sync_issues(milestones)
    sync_project(items)


if __name__ == "__main__":
    main()
