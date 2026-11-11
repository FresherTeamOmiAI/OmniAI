"""Sinh docs/tracking/OmniAI_Project_Tracking.xlsx từ backlog.py. Owner: HungDQ.

    tools/.venv/Scripts/python tools/tracking/generate_tracking.py          # Windows
    tools/.venv/bin/python tools/tracking/generate_tracking.py              # macOS/Linux

Chú ý: sinh lại sẽ GHI ĐÈ file (mất dữ liệu team đã nhập). Thêm --out để ghi ra file khác.
"""

import argparse
import sys
from collections import defaultdict
from datetime import timedelta
from pathlib import Path

from openpyxl import Workbook
from openpyxl.chart import BarChart, Reference
from openpyxl.formatting.rule import CellIsRule, FormulaRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.worksheet.datavalidation import DataValidation

from backlog import (BACKLOG, EPICS, ISSUES, MEMBER_NAMES, MEMBERS, PROJECT_END, PROJECT_START, QUESTIONS, SPRINT_GOALS,
                     TASK_TYPES, USE_CASES, sprint_code_of, sprint_dates, sprint_sheet_name)

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "docs" / "tracking" / "OmniAI_Project_Tracking.xlsx"

# ---------- style ----------
ACCENT = "A3245A"
HEADER_FILL = PatternFill("solid", fgColor="2D2640")
STORY_FILL = PatternFill("solid", fgColor="F3E6EC")
SECTION_FILL = PatternFill("solid", fgColor="E9E6F0")
FORMULA_FILL = PatternFill("solid", fgColor="F5F5F5")
RED_FILL = PatternFill("solid", fgColor="F8D0D0")
ORANGE_FILL = PatternFill("solid", fgColor="FBE3C4")
GREEN_FILL = PatternFill("solid", fgColor="D5EFD9")
GREY_FILL = PatternFill("solid", fgColor="E4E4E4")
WHITE_BOLD = Font(bold=True, color="FFFFFF")
BOLD = Font(bold=True)
TITLE = Font(bold=True, size=14, color=ACCENT)
THIN = Side(style="thin", color="D0CCDA")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
WRAP_TOP = Alignment(wrap_text=True, vertical="top")
DATE_FMT = "dd/mm/yyyy"

STATUS_TASK = ["Todo", "In-progress", "Review", "Done", "Blocked", "Canceled"]
TEST_RESULT = ["Chưa test", "Pass", "Fail"]
PO_ACCEPT = ["Chưa review", "Accepted", "Rejected"]
BUG_STATUS = ["New", "Open", "In-progress", "Fixed", "Verified", "Closed", "Rejected", "Reopened"]
BUG_SEVERITY = ["Critical", "Major", "Minor", "Trivial"]
PRIORITY = ["High", "Medium", "Low"]
BUG_TYPE = ["Functional", "UI", "AI-Hallucination", "Policy-Violation", "Adapter", "Performance",
            "Security-Privacy", "Document"]
CHANNELS = ["Web", "Shopee", "TikTok", "Simulator", "All"]
MODULES = ["Gateway/Adapter", "Policy", "Bench", "AI Brain", "Identity", "Customer", "Catalog", "Promotion",
           "Order", "Web Store", "Admin", "Infra", "Docs"]
ENVS = ["Local", "Dev (Docker)", "Demo"]
ISSUE_STATUS = ["Open", "Monitoring", "Closed"]
QA_STATUS = ["Open", "Answered", "Closed"]
SPRINT_CODES = [f"S{n}" for n in range(len(SPRINT_GOALS))] + ["Đệm"]

LISTS = {
    "Members": MEMBER_NAMES,
    "Reporters": MEMBER_NAMES + ["Thầy", "Khác"],
    "TaskStatus": STATUS_TASK,
    "TaskType": TASK_TYPES,
    "TestResult": TEST_RESULT,
    "POAccept": PO_ACCEPT,
    "BugStatus": BUG_STATUS,
    "Severity": BUG_SEVERITY,
    "Priority": PRIORITY,
    "BugType": BUG_TYPE,
    "Channels": CHANNELS,
    "Modules": MODULES,
    "Envs": ENVS,
    "Sprints": SPRINT_CODES,
    "IssueStatus": ISSUE_STATUS,
    "QAStatus": QA_STATUS,
}
LIST_REF: dict[str, str] = {}


def q(sheet: str) -> str:
    return "'" + sheet.replace("'", "''") + "'"


def header(ws, row: int, values: list[str], widths: list[int] | None = None):
    for col, value in enumerate(values, start=1):
        cell = ws.cell(row=row, column=col, value=value)
        cell.fill = HEADER_FILL
        cell.font = WHITE_BOLD
        cell.alignment = Alignment(wrap_text=True, vertical="center")
        cell.border = BORDER
    if widths:
        for col, width in enumerate(widths, start=1):
            ws.column_dimensions[get_column_letter(col)].width = width


def box(ws, row: int, cols: int, fill=None, bold=False):
    for col in range(1, cols + 1):
        cell = ws.cell(row=row, column=col)
        cell.border = BORDER
        cell.alignment = WRAP_TOP
        if fill:
            cell.fill = fill
        if bold:
            cell.font = BOLD


def validate(ws, list_name: str, ref: str):
    dv = DataValidation(type="list", formula1=LIST_REF[list_name], allow_blank=True)
    dv.error = "Chọn giá trị trong danh sách"
    dv.errorTitle = "Giá trị không hợp lệ"
    ws.add_data_validation(dv)
    dv.add(ref)


# ---------- sheets ----------

def build_lists(wb):
    ws = wb.create_sheet("_lists")
    for col, (name, values) in enumerate(LISTS.items(), start=1):
        letter = get_column_letter(col)
        ws.cell(row=1, column=col, value=name).font = BOLD
        for i, v in enumerate(values, start=2):
            ws.cell(row=i, column=col, value=v)
        # Dùng Named Range: Excel 2007 không cho data validation tham chiếu thẳng sang sheet khác.
        wb.defined_names[f"list{name}"] = DefinedName(
            f"list{name}", attr_text=f"_lists!${letter}$2:${letter}${len(values) + 1}")
        LIST_REF[name] = f"=list{name}"
    ws.sheet_state = "hidden"


def build_guide(wb):
    ws = wb.active
    ws.title = "Hướng dẫn"
    ws.column_dimensions["A"].width = 26
    ws.column_dimensions["B"].width = 110
    ws["A1"] = "OmniAI – Project Tracking"
    ws["A1"].font = TITLE
    ws["A2"] = (f"Bắt đầu {PROJECT_START:%d/%m/%Y} · Sprint 1 tuần (Thứ Ba → Thứ Hai) · {len(SPRINT_GOALS)} sprint · "
                f"từ S2 kéo lên 1 tuần, tuần cuối tới {PROJECT_END:%d/%m/%Y} là tuần đệm · 1 md = 5 giờ làm việc")
    row = 4
    ws.cell(row=row, column=1, value="Thành viên").font = BOLD
    ws.cell(row=row, column=2, value="Vai trò · capacity mặc định (md/sprint)").font = BOLD
    for name, role, cap in MEMBERS:
        row += 1
        ws.cell(row=row, column=1, value=name)
        ws.cell(row=row, column=2, value=f"{role} · {cap} md")
    sections = [
        ("Các sheet", "Dashboard: tổng hợp tiến độ, workload, bug.\n"
                      "Product Backlog (US): toàn bộ User Story + Acceptance Criteria, trạng thái lấy tự động từ sheet sprint.\n"
                      "UC: Use Case. WBS / Task of WBS / Effort: kế hoạch gốc (baseline) theo format mẫu.\n"
                      "Sprint n: backlog của sprint dạng User Story → Task, team cập nhật hằng ngày.\n"
                      "Report progress: daily report. Bug Log: log bug. Issues & Risks, Q&A."),
        ("Cập nhật task (mọi người)", "Mỗi ngày: vào sheet sprint hiện tại, sửa cột Status, Actual (md), Kết quả test, Bằng chứng (link commit/ảnh).\n"
                                      "Dòng User Story (tô hồng) là CÔNG THỨC, không sửa tay: Estimate/Actual/Status/% Done tự tính từ task.\n"
                                      "Thêm task phát sinh: điền vào dòng trống ngay dưới task cuối của US (cột Cấp = Task). Cần thêm nữa thì chèn dòng (Insert) PHÍA TRÊN dòng trống đó để công thức tự giãn."),
        ("Status task", "Todo → In-progress → Review (chờ người khác review code/test) → Done. Blocked: bị chặn, ghi lý do ở cột Ghi chú. Canceled: bỏ, không tính vào Planned."),
        ("Definition of Done", "Code đã merge vào dev · test/bench liên quan pass · có bằng chứng · PO nghiệm thu US (cột PO nghiệm thu = Accepted)."),
        ("PO nghiệm thu (HungDQ)", "Cuối sprint: test theo Acceptance Criteria của từng US, chọn Accepted/Rejected. Rejected thì ghi lý do và chuyển task sang sprint sau. Điền khối Sprint Review cuối sheet."),
        ("Daily report", "Mỗi ngày làm việc, ghi vào ô của mình trong sheet Report progress theo mẫu:\n- Hoàn thành:\n- Đang làm:\n- Sẽ làm:\n- Blocker:"),
        ("Log bug (mọi người, kể cả thầy góp ý)", "Thêm 1 dòng trong Bug Log: chọn Sprint, Module, Kênh, Loại, Severity; ghi Bước tái hiện / Mong đợi / Thực tế; Status = New.\n"
                                                  "Assignee nhận bug → Open/In-progress → Fixed (ghi commit ở Fixed in) → người báo kiểm lại → Verified → Closed.\n"
                                                  "Loại AI-Hallucination: AI bịa giá/sản phẩm/thông tin. Policy-Violation: câu khách nhận vẫn vi phạm luật kênh."),
        ("Severity", "Critical: sập hệ thống, lộ dữ liệu, vi phạm luật sàn tới khách. Major: chức năng chính sai. Minor: sai phụ, có cách né. Trivial: chính tả, giao diện nhỏ."),
        ("Chia sẻ file", "Đưa file lên Google Drive/OneDrive để cả team sửa cùng lúc. Bản trong repo là baseline; nếu giữ trong git thì chỉ HungDQ commit file này để tránh conflict."),
    ]
    row += 2
    for title, body in sections:
        ws.cell(row=row, column=1, value=title).font = BOLD
        cell = ws.cell(row=row, column=2, value=body)
        cell.alignment = WRAP_TOP
        ws.cell(row=row, column=1).alignment = WRAP_TOP
        ws.row_dimensions[row].height = 15 * (body.count("\n") + 2)
        row += 1


SPRINT_COLS = ["ID", "Cấp", "Tiêu đề / User story", "Acceptance Criteria / Cách test", "Loại", "Module", "Kênh",
               "Assignee", "Estimate (md)", "Actual (md)", "Status", "% Done", "Start", "Deadline", "Kết quả test",
               "Bằng chứng (link)", "PO nghiệm thu", "Bug liên quan / Ghi chú"]
SPRINT_WIDTHS = [9, 7, 46, 52, 8, 15, 14, 11, 10, 10, 12, 9, 11, 11, 12, 24, 14, 26]
BACKLOG_HEADER_ROW = 13
US_POS: dict[str, tuple[str, int]] = {}   # US id -> (sheet, row)
SPRINT_RANGE: dict[int, tuple[int, int]] = {}  # sprint -> (first row, last row) của khối backlog


def build_sprint(wb, n: int, stories: list[dict]):
    name = sprint_sheet_name(n)
    ws = wb.create_sheet(name)
    start, end = sprint_dates(n)
    ws["A1"] = f"Sprint {n}  ({start:%d/%m/%Y} – {end:%d/%m/%Y})"
    ws["A1"].font = TITLE
    ws["A2"], ws["C2"] = "Sprint goal", SPRINT_GOALS[n]
    ws["A3"], ws["C3"] = "Definition of Done", "Merge vào dev · test/bench pass · có bằng chứng · PO Accepted"
    for r in (2, 3):
        ws.cell(row=r, column=1).font = BOLD

    # Khối 1: workload theo người
    ws.cell(row=5, column=1, value="Workload & tiến độ theo người").font = BOLD
    wl_cols = ["Thành viên", "", "Capacity (md)", "Planned (md)", "Done (md)", "Actual (md)", "% hoàn thành", "Tải (Planned/Capacity)"]
    for col, v in enumerate(wl_cols, start=1):
        c = ws.cell(row=6, column=col, value=v)
        c.fill, c.font, c.border = HEADER_FILL, WHITE_BOLD, BORDER

    # Khối 2: backlog
    header(ws, BACKLOG_HEADER_ROW, SPRINT_COLS, SPRINT_WIDTHS)
    row = BACKLOG_HEADER_ROW + 1
    first = row
    for story in stories:
        us_row = row
        US_POS[story["id"]] = (name, us_row)
        t_first, t_last = us_row + 1, us_row + len(story["tasks"]) + 1  # +1 dòng trống dự phòng
        values = [story["id"], "Story", story["story"], story["ac"], "Story", story["module"], story["channel"],
                  story["owner"]]
        for col, v in enumerate(values, start=1):
            ws.cell(row=us_row, column=col, value=v)
        I, J, K = (f"{c}{t_first}:{c}{t_last}" for c in "IJK")
        ws.cell(row=us_row, column=9, value=f"=SUMIFS({I},{K},\"<>Canceled\")")
        ws.cell(row=us_row, column=10, value=f"=SUM({J})")
        ws.cell(row=us_row, column=12, value=f"=IFERROR(SUMIFS({I},{K},\"Done\")/I{us_row},0)")
        ws.cell(row=us_row, column=11, value=(
            f"=IF(COUNTA({K})=0,\"Todo\",IF(L{us_row}>=1,\"Done\",IF(COUNTIF({K},\"Blocked\")>0,\"Blocked\","
            f"IF(COUNTIF({K},\"Todo\")+COUNTIF({K},\"Canceled\")=COUNTA({K}),\"Todo\",\"In-progress\"))))"))
        ws.cell(row=us_row, column=13, value=start).number_format = DATE_FMT
        ws.cell(row=us_row, column=14, value=end).number_format = DATE_FMT
        ws.cell(row=us_row, column=17, value="Chưa review")
        box(ws, us_row, len(SPRINT_COLS), STORY_FILL, bold=True)
        ws.cell(row=us_row, column=12).number_format = "0%"
        ws.row_dimensions[us_row].height = 15 * max(3, story["ac"].count("\n") + 2)

        for i, task in enumerate(story["tasks"], start=1):
            r = us_row + i
            values = [f"T-{story['id'][3:]}.{i}", "Task", "   " + task["title"], task["test"], task["type"],
                      story["module"], story["channel"], task["assignee"], task["est"], None, "Todo", None,
                      start, end, "Chưa test"]
            for col, v in enumerate(values, start=1):
                ws.cell(row=r, column=col, value=v)
            ws.cell(row=r, column=12, value=f"=IF(K{r}=\"Done\",1,0)").number_format = "0%"
            for col in (13, 14):
                ws.cell(row=r, column=col).number_format = DATE_FMT
            box(ws, r, len(SPRINT_COLS))
        spare = t_last
        ws.cell(row=spare, column=3, value="   (dòng trống: thêm task phát sinh, nhớ chọn Cấp = Task)").font = Font(italic=True, color="8A8499")
        box(ws, spare, len(SPRINT_COLS))
        row = spare + 1
    last = row + 9  # thêm 10 dòng trống cho US phát sinh
    for r in range(row, last + 1):
        box(ws, r, len(SPRINT_COLS))
    SPRINT_RANGE[n] = (first, last)

    B, H_, I, J, K, Q = (f"${c}${first}:${c}${last}" for c in "BHIJKQ")
    for idx, (member, _, cap) in enumerate(MEMBERS):
        r = 7 + idx
        ws.cell(row=r, column=1, value=member)
        ws.cell(row=r, column=3, value=cap)
        ws.cell(row=r, column=4, value=f"=SUMIFS({I},{H_},A{r},{B},\"Task\")-SUMIFS({I},{H_},A{r},{B},\"Task\",{K},\"Canceled\")")
        ws.cell(row=r, column=5, value=f"=SUMIFS({I},{H_},A{r},{B},\"Task\",{K},\"Done\")")
        ws.cell(row=r, column=6, value=f"=SUMIFS({J},{H_},A{r},{B},\"Task\")")
        ws.cell(row=r, column=7, value=f"=IFERROR(E{r}/D{r},0)").number_format = "0%"
        ws.cell(row=r, column=8, value=f"=IFERROR(D{r}/C{r},0)").number_format = "0%"
        box(ws, r, 8)
    total = 7 + len(MEMBERS)
    ws.cell(row=total, column=1, value="Tổng")
    for col in "CDEF":
        ws[f"{col}{total}"] = f"=SUM({col}7:{col}{total - 1})"
    ws[f"G{total}"] = f"=IFERROR(E{total}/D{total},0)"
    ws[f"H{total}"] = f"=IFERROR(D{total}/C{total},0)"
    ws[f"G{total}"].number_format = ws[f"H{total}"].number_format = "0%"
    box(ws, total, 8, SECTION_FILL, bold=True)
    ws.conditional_formatting.add(f"H7:H{total}", CellIsRule(operator="greaterThan", formula=["1"], fill=RED_FILL))

    # Khối 3: Sprint review
    r = last + 2
    ws.cell(row=r, column=1, value="Sprint Review (PO điền cuối sprint)").font = TITLE
    review = [
        ("US Accepted / tổng US", f"=COUNTIFS({B},\"Story\",{Q},\"Accepted\")&\" / \"&COUNTIF({B},\"Story\")"),
        ("Velocity: md Done / md Planned", f"=SUMIFS({I},{B},\"Task\",{K},\"Done\")&\" / \"&D{total}"),
        ("Chuyển sang sprint sau", ""),
        ("Retro – Giữ", ""),
        ("Retro – Bỏ", ""),
        ("Retro – Thử", ""),
    ]
    for label, value in review:
        r += 1
        ws.cell(row=r, column=1, value=label).font = BOLD
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=2)
        ws.cell(row=r, column=3, value=value)
        ws.merge_cells(start_row=r, start_column=3, end_row=r, end_column=8)
        box(ws, r, 8)

    validate(ws, "TaskStatus", f"K{first}:K{last}")
    validate(ws, "Members", f"H{first}:H{last}")
    validate(ws, "TestResult", f"O{first}:O{last}")
    validate(ws, "POAccept", f"Q{first}:Q{last}")
    validate(ws, "TaskType", f"E{first}:E{last}")
    cf = f"K{first}:K{last}"
    ws.conditional_formatting.add(cf, CellIsRule(operator="equal", formula=['"Done"'], fill=GREEN_FILL))
    ws.conditional_formatting.add(cf, CellIsRule(operator="equal", formula=['"Blocked"'], fill=RED_FILL))
    ws.conditional_formatting.add(cf, CellIsRule(operator="equal", formula=['"In-progress"'], fill=ORANGE_FILL))
    ws.conditional_formatting.add(f"O{first}:O{last}", CellIsRule(operator="equal", formula=['"Fail"'], fill=RED_FILL))
    ws.conditional_formatting.add(f"Q{first}:Q{last}", CellIsRule(operator="equal", formula=['"Accepted"'], fill=GREEN_FILL))
    ws.conditional_formatting.add(f"Q{first}:Q{last}", CellIsRule(operator="equal", formula=['"Rejected"'], fill=RED_FILL))
    ws.freeze_panes = f"D{BACKLOG_HEADER_ROW + 1}"
    ws.auto_filter.ref = f"A{BACKLOG_HEADER_ROW}:R{last}"


def build_backlog_sheet(wb):
    ws = wb.create_sheet("Product Backlog (US)", 1)
    ws["A1"] = "Product Backlog – phương pháp MoSCoW"
    ws["A1"].font = TITLE
    ws["A2"] = ("Bối cảnh: shop mỹ phẩm bán trên Shopee, TikTok Shop và web riêng; mỗi kênh một trí nhớ, khách phải khai lại "
                "loại da, bot sàn không được nhắc kênh khác. → Một bộ não AI dùng chung, mỗi kênh có luật riêng, "
                "nhớ khách xuyên kênh khi khách đồng ý liên kết, đo chất lượng bằng bench.")
    ws["A2"].alignment = WRAP_TOP
    ws.merge_cells("A2:M2")
    ws.row_dimensions[2].height = 45
    cols = ["ID", "Epic", "MoSCoW", "Priority", "User story", "Acceptance Criteria", "Sprint", "Owner",
            "Estimate (md)", "% Done", "Status", "PO nghiệm thu", "Ghi chú"]
    header(ws, 4, cols, [8, 24, 12, 9, 52, 56, 9, 10, 11, 9, 12, 14, 22])
    r = 4
    for epic_code, epic_name in EPICS.items():
        r += 1
        ws.cell(row=r, column=1, value=f"Epic {epic_code}: {epic_name}")
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=len(cols))
        box(ws, r, len(cols), SECTION_FILL, bold=True)
        for story in [s for s in BACKLOG if s["epic"] == epic_code]:
            r += 1
            sheet, us_row = US_POS[story["id"]]
            ref = lambda col: f"={q(sheet)}!{col}{us_row}"
            values = [story["id"], epic_name, story["moscow"], story["priority"], story["story"], story["ac"],
                      f"S{story['sprint']}", story["owner"], ref("I"), ref("L"), ref("K"), ref("Q"), story["title"]]
            for col, v in enumerate(values, start=1):
                ws.cell(row=r, column=col, value=v)
            box(ws, r, len(cols))
            for col in (9, 10, 11, 12):
                ws.cell(row=r, column=col).fill = FORMULA_FILL
            ws.cell(row=r, column=10).number_format = "0%"
            ws.row_dimensions[r].height = 15 * max(3, story["ac"].count("\n") + 2)
    ws.freeze_panes = "B5"
    ws.auto_filter.ref = f"A4:M{r}"
    ws.conditional_formatting.add(f"K5:K{r}", CellIsRule(operator="equal", formula=['"Done"'], fill=GREEN_FILL))
    ws.conditional_formatting.add(f"L5:L{r}", CellIsRule(operator="equal", formula=['"Accepted"'], fill=GREEN_FILL))


def build_uc(wb):
    ws = wb.create_sheet("UC", 2)
    header(ws, 1, ["UC-ID", "Tên Use Case", "Actor chính", "Mô tả Use Case", "Condition (Pre/Post)",
                   "Alternative Flow", "Exception Flow", "Business Rule"], [8, 30, 18, 50, 36, 34, 34, 44])
    for r, uc in enumerate(USE_CASES, start=2):
        for col, v in enumerate(uc, start=1):
            ws.cell(row=r, column=col, value=v)
        box(ws, r, 8)
        ws.row_dimensions[r].height = 48
    ws.freeze_panes = "C2"


def level_of(est: float) -> str:
    return "Simple" if est <= 2.5 else "Medium" if est <= 4.5 else "Complex"


def build_wbs(wb):
    ws = wb.create_sheet("WBS", 3)
    header(ws, 1, ["Function/Screen", "Feature", "Sub Feature", "Function/Screen Description", "Level*",
                   "Est. Effort (md)", "Priority", "Planned", "Owner", "Status", "Notes"],
           [32, 28, 16, 56, 10, 11, 9, 10, 10, 12, 10])
    for r, story in enumerate(BACKLOG, start=2):
        est = sum(t["est"] for t in story["tasks"])
        sheet, us_row = US_POS[story["id"]]
        values = [story["title"], EPICS[story["epic"]], story["module"], story["story"], level_of(est), est,
                  story["priority"], f"Sprint {story['sprint']}", story["owner"], f"={q(sheet)}!K{us_row}", story["id"]]
        for col, v in enumerate(values, start=1):
            ws.cell(row=r, column=col, value=v)
        box(ws, r, 11)
    ws.cell(row=len(BACKLOG) + 3, column=1, value="Level*: Simple ≤ 2.5 md · Medium ≤ 4.5 md · Complex > 4.5 md (theo tổng effort task)").font = Font(italic=True)
    ws.freeze_panes = "B2"
    ws.auto_filter.ref = f"A1:K{len(BACKLOG) + 1}"


def build_task_of_wbs(wb):
    ws = wb.create_sheet("Task of WBS", 4)
    header(ws, 1, ["Task ID", "US ID", "Function/Screen", "Task", "Loại", "Assignee", "Est. Effort (md)", "Planned"],
           [9, 8, 32, 52, 8, 11, 11, 10])
    r = 1
    for story in BACKLOG:
        for i, task in enumerate(story["tasks"], start=1):
            r += 1
            values = [f"T-{story['id'][3:]}.{i}", story["id"], story["title"], task["title"], task["type"],
                      task["assignee"], task["est"], f"Sprint {story['sprint']}"]
            for col, v in enumerate(values, start=1):
                ws.cell(row=r, column=col, value=v)
            box(ws, r, 8)
    ws.freeze_panes = "C2"
    ws.auto_filter.ref = f"A1:H{r}"
    return r


def build_effort(wb, task_last_row: int):
    ws = wb.create_sheet("Effort", 5)
    ws["A1"] = f"OMNIAI – FULL WBS EFFORT | {len(MEMBERS)} người × {len(SPRINT_GOALS)} sprint (1 tuần) | 1 man-day = 5 productive hours"
    ws["A1"].font = TITLE
    ws["A2"] = ("Effort rule: Simple ≤ 2.5 md | Medium ≤ 4.5 md | Complex > 4.5 md · "
                "Tỉ lệ tham khảo: SRS 10% · API 10% · UI 10% · FE 20% · BE/AI 35% · Test/Bench 15%")
    cols = ["STT", "Feature / Function", "Module", "Sprint", "Complexity"] + TASK_TYPES + ["TOTAL (md)"]
    header(ws, 3, cols, [6, 34, 15, 8, 11] + [7] * len(TASK_TYPES) + [11])
    tw = f"'Task of WBS'!"
    for i, story in enumerate(BACKLOG, start=1):
        r = 3 + i
        est = sum(t["est"] for t in story["tasks"])
        for col, v in enumerate([i, f"{story['id']} {story['title']}", story["module"], f"S{story['sprint']}",
                                 level_of(est)], start=1):
            ws.cell(row=r, column=col, value=v)
        for j, ttype in enumerate(TASK_TYPES):
            col = 6 + j
            ws.cell(row=r, column=col, value=(
                f"=SUMIFS({tw}$G$2:$G${task_last_row},{tw}$B$2:$B${task_last_row},\"{story['id']}\","
                f"{tw}$E$2:$E${task_last_row},\"{ttype}\")"))
        total_col = 6 + len(TASK_TYPES)
        ws.cell(row=r, column=total_col,
                value=f"=SUM({get_column_letter(6)}{r}:{get_column_letter(total_col - 1)}{r})")
        box(ws, r, total_col)
    last = 3 + len(BACKLOG)
    r = last + 1
    ws.cell(row=r, column=2, value="TỔNG")
    for col in range(6, 7 + len(TASK_TYPES)):
        letter = get_column_letter(col)
        ws.cell(row=r, column=col, value=f"=SUM({letter}4:{letter}{last})")
    box(ws, r, 6 + len(TASK_TYPES), SECTION_FILL, bold=True)
    r += 1
    ws.cell(row=r, column=2, value="Tỉ lệ theo loại")
    total_letter = get_column_letter(6 + len(TASK_TYPES))
    for col in range(6, 6 + len(TASK_TYPES)):
        letter = get_column_letter(col)
        c = ws.cell(row=r, column=col, value=f"=IFERROR({letter}{r - 1}/${total_letter}${r - 1},0)")
        c.number_format = "0%"
    box(ws, r, 6 + len(TASK_TYPES), SECTION_FILL)
    ws.freeze_panes = "C4"


def build_dashboard(wb):
    ws = wb.create_sheet("Dashboard", 1)
    ws["A1"] = "Dashboard – PO/Lead theo dõi"
    ws["A1"].font = TITLE
    ws["A2"] = "Tự tính từ các sheet Sprint và Bug Log. Không sửa tay."

    # Bảng 1: tiến độ theo sprint
    cols = ["Sprint", "Từ", "Đến", "Sprint goal", "Số US", "US Accepted", "Planned (md)", "Done (md)", "% Done"]
    header(ws, 4, cols, [22, 11, 11, 48, 8, 11, 12, 11, 9, 12, 12, 12, 12])
    for n in range(len(SPRINT_GOALS)):
        r = 5 + n
        name = sprint_sheet_name(n)
        first, last = SPRINT_RANGE[n]
        s = q(name)
        B, I, K, Q = (f"{s}!${c}${first}:${c}${last}" for c in "BIKQ")
        start, end = sprint_dates(n)
        values = [name, start, end, SPRINT_GOALS[n],
                  f"=COUNTIF({B},\"Story\")",
                  f"=COUNTIFS({B},\"Story\",{Q},\"Accepted\")",
                  f"=SUMIFS({I},{B},\"Task\")-SUMIFS({I},{B},\"Task\",{K},\"Canceled\")",
                  f"=SUMIFS({I},{B},\"Task\",{K},\"Done\")",
                  f"=IFERROR(H{r}/G{r},0)"]
        for col, v in enumerate(values, start=1):
            ws.cell(row=r, column=col, value=v)
        ws.cell(row=r, column=2).number_format = ws.cell(row=r, column=3).number_format = DATE_FMT
        ws.cell(row=r, column=9).number_format = "0%"
        box(ws, r, len(cols))
    sprint_last = 4 + len(SPRINT_GOALS)

    chart = BarChart()
    chart.type = "col"
    chart.title = "Velocity: Planned vs Done (md)"
    chart.y_axis.title = "man-day"
    chart.add_data(Reference(ws, min_col=7, max_col=8, min_row=4, max_row=sprint_last), titles_from_data=True)
    chart.set_categories(Reference(ws, min_col=1, min_row=5, max_row=sprint_last))
    chart.height, chart.width = 8, 22
    ws.add_chart(chart, "K4")

    # Bảng 2: workload theo người x sprint
    r0 = sprint_last + 3
    ws.cell(row=r0 - 1, column=1, value="Planned (md) theo người · tô đỏ khi vượt capacity").font = BOLD
    header_vals = ["Sprint"] + MEMBER_NAMES
    for col, v in enumerate(header_vals, start=1):
        c = ws.cell(row=r0, column=col, value=v)
        c.fill, c.font, c.border = HEADER_FILL, WHITE_BOLD, BORDER
    for n in range(len(SPRINT_GOALS)):
        r = r0 + 1 + n
        ws.cell(row=r, column=1, value=f"S{n}")
        s = q(sprint_sheet_name(n))
        for idx in range(len(MEMBERS)):
            ws.cell(row=r, column=2 + idx, value=f"={s}!D{7 + idx}")
        box(ws, r, 1 + len(MEMBERS))
    wl_last = r0 + len(SPRINT_GOALS)
    for idx, (_, _, cap) in enumerate(MEMBERS):
        letter = get_column_letter(2 + idx)
        ws.conditional_formatting.add(f"{letter}{r0 + 1}:{letter}{wl_last}",
                                      CellIsRule(operator="greaterThan", formula=[str(cap)], fill=RED_FILL))

    # Bảng 3: bug
    r = wl_last + 3
    ws.cell(row=r - 1, column=1, value="Bug (mở = khác Closed/Rejected)").font = BOLD
    bug = "'Bug Log'!"
    status_col, sev_col, assignee_col, type_col, module_col = "P", "M", "O", "F", "D"
    open_crit = f"{bug}${status_col}$2:${status_col}$1000,\"<>Closed\",{bug}${status_col}$2:${status_col}$1000,\"<>Rejected\""
    for col, v in enumerate(["Severity", "Đang mở", "", "Status", "Số bug", "", "Assignee", "Đang mở"], start=1):
        if v:
            c = ws.cell(row=r, column=col, value=v)
            c.fill, c.font, c.border = HEADER_FILL, WHITE_BOLD, BORDER
    for i, sev in enumerate(BUG_SEVERITY):
        ws.cell(row=r + 1 + i, column=1, value=sev)
        ws.cell(row=r + 1 + i, column=2,
                value=f"=COUNTIFS({bug}${sev_col}$2:${sev_col}$1000,A{r + 1 + i},{open_crit})")
    for i, st in enumerate(BUG_STATUS):
        ws.cell(row=r + 1 + i, column=4, value=st)
        ws.cell(row=r + 1 + i, column=5, value=f"=COUNTIF({bug}${status_col}$2:${status_col}$1000,D{r + 1 + i})")
    for i, m in enumerate(MEMBER_NAMES):
        ws.cell(row=r + 1 + i, column=7, value=m)
        ws.cell(row=r + 1 + i, column=8,
                value=f"=COUNTIFS({bug}${assignee_col}$2:${assignee_col}$1000,G{r + 1 + i},{open_crit})")
    r2 = r + len(BUG_STATUS) + 3
    for col, v in enumerate(["Loại bug", "Đang mở", "", "Module", "Đang mở"], start=1):
        if v:
            c = ws.cell(row=r2, column=col, value=v)
            c.fill, c.font, c.border = HEADER_FILL, WHITE_BOLD, BORDER
    for i, t in enumerate(BUG_TYPE):
        ws.cell(row=r2 + 1 + i, column=1, value=t)
        ws.cell(row=r2 + 1 + i, column=2,
                value=f"=COUNTIFS({bug}${type_col}$2:${type_col}$1000,A{r2 + 1 + i},{open_crit})")
    for i, m in enumerate(MODULES):
        ws.cell(row=r2 + 1 + i, column=4, value=m)
        ws.cell(row=r2 + 1 + i, column=5,
                value=f"=COUNTIFS({bug}${module_col}$2:${module_col}$1000,D{r2 + 1 + i},{open_crit})")
    ws.conditional_formatting.add(f"B{r + 1}:B{r + 2}", CellIsRule(operator="greaterThan", formula=["0"], fill=RED_FILL))


BUG_COLS = ["ID", "Ngày phát hiện", "Sprint", "Module", "Kênh", "Loại", "Tiêu đề", "Bước tái hiện", "Kết quả mong đợi",
            "Kết quả thực tế", "Môi trường", "Người báo", "Severity", "Priority", "Assignee", "Status", "Fixed in (commit)",
            "Ngày đóng", "Ghi chú"]


def build_bug_log(wb, rows: int = 200):
    ws = wb.create_sheet("Bug Log")
    header(ws, 1, BUG_COLS, [9, 12, 7, 15, 10, 16, 34, 40, 30, 30, 12, 10, 10, 9, 10, 11, 16, 11, 24])
    for i in range(1, rows + 1):
        r = 1 + i
        ws.cell(row=r, column=1, value=f"BUG-{i:03d}")
        ws.cell(row=r, column=2).number_format = DATE_FMT
        ws.cell(row=r, column=18).number_format = DATE_FMT
        box(ws, r, len(BUG_COLS))
    last = rows + 1
    for list_name, col in [("Sprints", "C"), ("Modules", "D"), ("Channels", "E"), ("BugType", "F"), ("Envs", "K"),
                           ("Reporters", "L"), ("Severity", "M"), ("Priority", "N"), ("Members", "O"),
                           ("BugStatus", "P")]:
        validate(ws, list_name, f"{col}2:{col}{last}")
    sev = f"M2:M{last}"
    ws.conditional_formatting.add(sev, CellIsRule(operator="equal", formula=['"Critical"'], fill=RED_FILL, font=Font(bold=True)))
    ws.conditional_formatting.add(sev, CellIsRule(operator="equal", formula=['"Major"'], fill=ORANGE_FILL))
    ws.conditional_formatting.add(f"A2:S{last}", FormulaRule(formula=['OR($P2="Closed",$P2="Rejected")'], fill=GREY_FILL))
    ws.conditional_formatting.add(f"P2:P{last}", CellIsRule(operator="equal", formula=['"Verified"'], fill=GREEN_FILL))
    ws.conditional_formatting.add(f"P2:P{last}", CellIsRule(operator="equal", formula=['"Reopened"'], fill=RED_FILL))
    ws.freeze_panes = "H2"
    ws.auto_filter.ref = f"A1:S{last}"


def build_report_progress(wb):
    ws = wb.create_sheet("Report progress")
    header(ws, 1, ["Ngày", "Sprint"] + MEMBER_NAMES, [12, 8] + [46] * len(MEMBERS))
    ws.cell(row=2, column=1, value="Mẫu")
    for idx in range(len(MEMBERS)):
        ws.cell(row=2, column=3 + idx, value="- Hoàn thành:\n- Đang làm:\n- Sẽ làm:\n- Blocker:")
    box(ws, 2, 2 + len(MEMBERS), SECTION_FILL)
    ws.row_dimensions[2].height = 62
    day, r = PROJECT_START, 3
    while day <= PROJECT_END:
        if day.weekday() < 6:  # Thứ Hai - Thứ Bảy
            ws.cell(row=r, column=1, value=day).number_format = DATE_FMT
            ws.cell(row=r, column=2, value=sprint_code_of(day))
            box(ws, r, 2 + len(MEMBERS))
            ws.row_dimensions[r].height = 60
            r += 1
        day += timedelta(days=1)
    ws.freeze_panes = "C3"


def build_issues(wb):
    ws = wb.create_sheet("Issues & Risks")
    header(ws, 1, ["Ngày", "Issue / Risk", "Loại", "Ảnh hưởng", "Khả năng xảy ra", "Priority", "Owner",
                   "Due date", "Status", "Cách xử lý / Ghi chú"], [11, 46, 8, 11, 13, 9, 10, 11, 11, 56])
    for i, (issue, kind, impact, likelihood, prio, owner, mitigation) in enumerate(ISSUES, start=2):
        values = [PROJECT_START, issue, kind, impact, likelihood, prio, owner, None, "Open", mitigation]
        for col, v in enumerate(values, start=1):
            ws.cell(row=i, column=col, value=v)
        ws.cell(row=i, column=1).number_format = DATE_FMT
        ws.cell(row=i, column=8).number_format = DATE_FMT
        box(ws, i, 10)
    last = 60
    for r in range(len(ISSUES) + 2, last + 1):
        box(ws, r, 10)
    validate(ws, "Priority", f"F2:F{last}")
    validate(ws, "Members", f"G2:G{last}")
    validate(ws, "IssueStatus", f"I2:I{last}")
    ws.freeze_panes = "C2"


def build_qa(wb):
    ws = wb.create_sheet("Q&A")
    header(ws, 1, ["Ngày", "Câu hỏi", "Hỏi ai / Ai hỏi", "Priority", "Due date", "Status", "Trả lời / Ghi chú"],
           [11, 70, 16, 9, 11, 11, 56])
    for i, question in enumerate(QUESTIONS, start=2):
        for col, v in enumerate([PROJECT_START, question, "Thầy hướng dẫn", "High", None, "Open"], start=1):
            ws.cell(row=i, column=col, value=v)
        ws.cell(row=i, column=1).number_format = DATE_FMT
        box(ws, i, 7)
        ws.row_dimensions[i].height = 32
    last = 60
    for r in range(len(QUESTIONS) + 2, last + 1):
        box(ws, r, 7)
    validate(ws, "Priority", f"D2:D{last}")
    validate(ws, "QAStatus", f"F2:F{last}")


# ---------- kiểm tra kế hoạch ----------

def check_plan() -> list[str]:
    warnings = []
    caps = {name: cap for name, _, cap in MEMBERS}
    load = defaultdict(float)
    for story in BACKLOG:
        if story["priority"] == "P0" and not story["ac"].strip():
            warnings.append(f"{story['id']} (P0) thiếu Acceptance Criteria")
        for task in story["tasks"]:
            if task["assignee"] not in caps:
                warnings.append(f"{story['id']}: assignee lạ '{task['assignee']}'")
            if task["type"] not in TASK_TYPES:
                warnings.append(f"{story['id']}: loại task lạ '{task['type']}'")
            load[(story["sprint"], task["assignee"])] += task["est"]
    for (sprint, member), md in sorted(load.items()):
        if md > caps.get(member, 0):
            warnings.append(f"S{sprint} {member}: {md} md > capacity {caps[member]} md")
    ids = [s["id"] for s in BACKLOG]
    if len(ids) != len(set(ids)):
        warnings.append("Trùng ID User Story")
    return warnings


def print_load():
    load = defaultdict(float)
    for story in BACKLOG:
        for task in story["tasks"]:
            load[(story["sprint"], task["assignee"])] += task["est"]
    print("Sprint | " + " | ".join(f"{m:>7}" for m in MEMBER_NAMES))
    for n in range(len(SPRINT_GOALS)):
        print(f"S{n:<5} | " + " | ".join(f"{load[(n, m)]:>7.1f}" for m in MEMBER_NAMES))


def main() -> int:
    # Console Windows (cp1252) không in được tiếng Việt, print cảnh báo sẽ ném UnicodeEncodeError.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    args = parser.parse_args()

    warnings = check_plan()
    print_load()
    for w in warnings:
        print("CẢNH BÁO:", w)

    wb = Workbook()
    build_guide(wb)
    build_lists(wb)
    for n in range(len(SPRINT_GOALS)):
        build_sprint(wb, n, [s for s in BACKLOG if s["sprint"] == n])
    build_backlog_sheet(wb)
    build_uc(wb)
    build_wbs(wb)
    task_last = build_task_of_wbs(wb)
    build_effort(wb, task_last)
    build_dashboard(wb)
    build_report_progress(wb)
    build_bug_log(wb)
    build_issues(wb)
    build_qa(wb)

    # Thứ tự sheet: Hướng dẫn, Dashboard, Product Backlog, UC, WBS, Task of WBS, Effort, Sprint..., còn lại
    order = ["Hướng dẫn", "Dashboard", "Product Backlog (US)", "UC", "WBS", "Task of WBS", "Effort"]
    order += [sprint_sheet_name(n) for n in range(len(SPRINT_GOALS))]
    order += ["Report progress", "Bug Log", "Issues & Risks", "Q&A", "_lists"]
    wb._sheets = [wb[name] for name in order]
    wb.active = 1

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out)
    print(f"Đã ghi {out} ({len(BACKLOG)} US, {sum(len(s['tasks']) for s in BACKLOG)} task)")
    return 1 if warnings else 0


if __name__ == "__main__":
    raise SystemExit(main())
