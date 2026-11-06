# QUY TRÌNH QUẢN LÝ GIT VÀ PHỐI HỢP LÀM VIỆC NHÓM

## 1. Mục tiêu

Tài liệu này quy định quy trình làm việc Git thống nhất cho toàn bộ thành viên dự án nhằm:

* Giảm thiểu xung đột mã nguồn (Merge Conflict).
* Đảm bảo nhánh chính luôn ổn định.
* Dễ dàng kiểm soát lịch sử thay đổi.
* Tăng hiệu quả phối hợp giữa các thành viên.
* Đảm bảo chất lượng mã nguồn trước khi tích hợp.

---

# 2. Mô hình phân nhánh

Dự án sử dụng mô hình:

**Git Flow (rút gọn — không dùng nhánh `release/*`)**

Dựa trên [A Successful Git Branching Model](https://nvie.com/posts/a-successful-git-branching-model/).

## Các nhánh lâu dài

| Nhánh  | Vai trò                     | Nhận thay đổi từ                                         |
| ------ | --------------------------- | -------------------------------------------------------- |
| `main` | Production, gắn tag version | PR từ `dev` (phát hành), PR từ `hotfix/*`                |
| `dev`  | Tích hợp (integration)      | PR từ `feature/*`, `bugfix/*`, `docs/*`, `chore/*`; back-merge từ `main` |

Nguyên tắc:

* `main` luôn ở trạng thái ổn định và có thể triển khai (Deploy).
* Mỗi lần merge vào `main` đều được gắn tag `vX.Y.Z`.
* `dev` luôn build được và chứa code mới nhất đã được review.
* **Không commit / push trực tiếp** lên `main` và `dev` — mọi thay đổi đi qua Pull Request.

---

## Các loại nhánh tạm thời

| Loại             | Tiền tố    | Tách từ | Merge vào                           |
| ---------------- | ---------- | ------- | ----------------------------------- |
| Tính năng mới    | feature/   | `dev`   | `dev`                               |
| Sửa lỗi          | bugfix/    | `dev`   | `dev`                               |
| Sửa lỗi khẩn cấp | hotfix/    | `main`  | `main`, sau đó back-merge vào `dev` |
| Tài liệu         | docs/      | `dev`   | `dev`                               |
| Bảo trì hệ thống | chore/     | `dev`   | `dev`                               |

Ví dụ:

```text
feature/PROJ-101-user-login
bugfix/PROJ-102-jwt-leak
hotfix/PROJ-201-fix-login-crash
docs/api-guide
chore/update-eslint
```

---

## Sơ đồ

```text
main      v1.0 ●─────────────● v1.1 ──────────────● v1.1.1 ──────────────● v1.2
                             ↑     ↘              ↑      ↘                ↑
hotfix/*                     │      ● fix ───── ●       │                │
                             │                          │ back-merge     │
                             │ phát hành                ↓                │ phát hành
dev       ●───────●──────────●─────────────●────────────●────────────────●
                   ↘        ↗               ↘          ↗
feature/*           ●──────●                 ●────────●
                (branch off)  (PR, ≥1 approve, merge)
```

* `feature/*`: tách từ `dev` → PR vào `dev`.
* `dev` → `main`: khi phát hành, gắn tag.
* `hotfix/*`: tách từ `main` → PR vào `main` + tag → back-merge `main` vào `dev`.

---

# 3. Quy tắc đặt tên nhánh

Định dạng:

```text
<type>/<ticket-id>-<mo-ta-ngan>
```

Ví dụ:

```text
feature/PROJ-101-user-login
bugfix/PROJ-102-jwt-leak
hotfix/PROJ-201-fix-login-crash
```

Quy định:

* Chỉ sử dụng chữ thường.
* Sử dụng dấu "-" để nối từ.
* Không dùng dấu cách.
* Không dùng dấu "_".
* Không dùng ký tự đặc biệt.

---

# 4. Quy trình làm việc hàng ngày

## Bước 1: Đồng bộ đầu ngày

Trước khi bắt đầu làm việc:

```bash
git checkout dev
git pull
```

Mục tiêu:

* Nhận toàn bộ thay đổi mới nhất từ nhóm.
* Đảm bảo làm việc trên phiên bản mới nhất.

---

## Bước 2: Tạo nhánh mới

Mỗi task phải có một nhánh riêng, tách từ `dev`.

Ví dụ:

```bash
git checkout -b feature/PROJ-101-user-login
```

Không làm việc trực tiếp trên:

```text
main
dev
```

---

## Bước 3: Push giữa ngày

Trước giờ nghỉ trưa:

```bash
git push origin feature/PROJ-101-user-login
```

Mục đích:

* Sao lưu tiến độ.
* Tránh mất dữ liệu khi gặp sự cố.

---

## Bước 4: Đồng bộ buổi chiều

Tối thiểu một lần mỗi ngày:

```bash
git fetch origin
git rebase origin/dev
```

Mục tiêu:

* Nhận thay đổi mới nhất từ đồng đội.
* Giải quyết xung đột sớm.

---

## Bước 5: Push cuối ngày

Trước khi kết thúc công việc:

```bash
git push origin feature/PROJ-101-user-login
```

Tuyệt đối không để công việc chỉ tồn tại trên máy cá nhân.

---

## Lưu ý: Khi nhánh bị behind hoặc push/pull bị lỗi

Khi người khác đã được merge PR vào `dev` trước mình, nhánh của mình sẽ bị **behind** (thiếu code mới). **Bắt buộc phải kéo code mới về rồi mới code tiếp hoặc tạo/merge PR.**

### Dấu hiệu nhận biết

* `git push` bị từ chối:

```text
! [rejected]        feature/PROJ-101-user-login -> feature/PROJ-101-user-login (non-fast-forward)
error: failed to push some refs ...
hint: Updates were rejected because the tip of your current branch is behind
```

* `git status` báo: `Your branch is behind 'origin/...' by N commits`.
* Trên GitHub, PR hiển thị **"This branch is out-of-date with the base branch"** hoặc **"This branch has conflicts that must be resolved"**.

### Trường hợp 1: Nhánh feature bị behind so với `dev`

```bash
# Cất code đang làm dở (hoặc commit trước)
git stash

# Kéo dev mới nhất và rebase nhánh của mình lên trên
git fetch origin
git rebase origin/dev

# Lấy lại code đang làm dở
git stash pop

# Build + chạy test lại, sau đó push
git push --force-with-lease origin feature/PROJ-101-user-login
```

### Trường hợp 2: Push bị từ chối trên chính nhánh của mình

Xảy ra khi nhánh remote có commit mà máy mình chưa có (push từ máy khác, hoặc có người cùng làm trên nhánh).

```bash
git pull --rebase origin feature/PROJ-101-user-login
git push origin feature/PROJ-101-user-login
```

### Trường hợp 3: Gặp conflict khi rebase

```bash
# 1. Xem file bị conflict
git status

# 2. Mở file, sửa các đoạn <<<<<<< ======= >>>>>>>, giữ đúng logic của cả hai bên

# 3. Đánh dấu đã xử lý và tiếp tục
git add <file>
git rebase --continue

# Nếu rối, hủy rebase để quay về trạng thái trước đó
git rebase --abort
```

### Quy định

* **Không code tiếp trên nhánh đang behind** — luôn đồng bộ trước rồi mới làm.
* Sau khi rebase / xử lý conflict, **phải build và chạy test lại** trước khi push.
* Chỉ dùng `--force-with-lease`, **không dùng `--force`**, và chỉ trên nhánh của chính mình.
* **Tuyệt đối không force push** lên `main` và `dev`.
* Không tự ý xóa code của người khác khi xử lý conflict — nếu không chắc, hỏi tác giả đoạn code đó.
* Nếu không tự xử lý được, báo Lead / người review trước khi thao tác tiếp.

---

# 5. Quy trình Feature → dev

Áp dụng cho `feature/*`, `bugfix/*`, `docs/*`, `chore/*`.

## Tổng quan luồng

```text
[Máy cá nhân - local]                 [GitHub - remote]
 dev ──(checkout -b)──► feature/...
                            │ code + commit
                            │
                            └──(git push -u)──► origin/feature/...
                                                     │
                                                     └──(tạo PR trên GitHub)──► review (≥1 approve) ──► merge vào dev
```

Lưu ý:

* Nhánh tạo bằng `git checkout -b` **chỉ tồn tại trên máy mình**, người khác chưa thấy.
* Chỉ sau khi `git push` thì nhánh mới **xuất hiện trên GitHub (remote)**.
* Chỉ khi nhánh đã có trên remote thì mới **tạo được Pull Request**.

## Bước 1: Tạo nhánh ở local, tách từ `dev`

```bash
git checkout dev
git pull
git checkout -b feature/PROJ-101-user-login
```

## Bước 2: Code và commit ở local

```bash
git add <file>
git commit -m "feat(auth): add login endpoint"
```

## Bước 3: Rebase với `dev` mới nhất

```bash
git fetch origin
git rebase origin/dev
```

Nếu bị conflict → xem mục **"Lưu ý: Khi nhánh bị behind hoặc push/pull bị lỗi"** ở trên.

## Bước 4: Đẩy nhánh lên remote

Lần đầu push (tạo nhánh trên GitHub và liên kết nhánh local với remote):

```bash
git push -u origin feature/PROJ-101-user-login
```

Các lần push sau chỉ cần:

```bash
git push
# hoặc, sau khi rebase:
git push --force-with-lease
```

## Bước 5: Tạo Pull Request trên GitHub

1. Vào repo trên GitHub → bấm **"Compare & pull request"** (hiện ra sau khi push), hoặc vào tab **Pull requests → New pull request**.
2. Chọn **base: `dev`** ← **compare: `feature/PROJ-101-user-login`**.
   (Kiểm tra kỹ base là `dev`, **không phải** `main`.)
3. Tiêu đề PR theo Conventional Commits, kèm mã task, ví dụ: `feat(auth): add login endpoint [PROJ-101]`.
4. Mô tả: làm gì, cách test, ảnh chụp (nếu có UI).
5. Dán kết quả test/bench đã chạy ở máy mình vào mô tả PR.
6. **Không cần gán Reviewer cụ thể.** Ai trong team rảnh thì vào review và approve. Không tự approve PR của chính mình.

Hoặc dùng GitHub CLI:

```bash
gh pr create --base dev --title "feat(auth): add login endpoint [PROJ-101]"
```

## Bước 6: Review và merge

1. Cần **1 approve** của bất kỳ thành viên nào khác (ruleset của `main`/`dev` đã bắt buộc điều này). Không review chéo theo cặp cố định. Nếu có vấn đề thì cả team họp để giải quyết.
2. Nếu reviewer yêu cầu sửa: sửa ở local → commit → `git push` (PR tự cập nhật, **không cần tạo PR mới**).
3. Đủ điều kiện (xem Mục 8) → merge vào `dev`.
4. Xóa nhánh feature trên GitHub (nút **Delete branch**) và ở local:

```bash
git checkout dev
git pull
git branch -d feature/PROJ-101-user-login
```

---

# 6. Quy trình Hotfix

Dùng khi có lỗi nghiêm trọng trên production (`main`) cần sửa gấp, không chờ chu kỳ phát hành từ `dev`.

```bash
# 1. Tạo nhánh hotfix từ main
git checkout main
git pull
git checkout -b hotfix/PROJ-201-fix-login-crash

# 2. Sửa lỗi + commit
git add <file>
git commit -m "fix(auth): handle null token on login"

# 3. Đẩy nhánh hotfix lên remote
git push -u origin hotfix/PROJ-201-fix-login-crash
```

4. Tạo Pull Request trên GitHub (các bước như Mục 5 – Bước 5) nhưng chọn **base = `main`** ← compare = `hotfix/PROJ-201-fix-login-crash`. Cần ít nhất 1 người approve, rồi merge.
5. Gắn tag version mới (tăng PATCH):

```bash
git checkout main
git pull
git tag -a v1.1.1 -m "hotfix: fix login crash"
git push origin v1.1.1
```

6. **Back-merge `main` → `dev`** (bắt buộc, làm ngay sau bước 5):

```bash
git checkout dev
git pull
git merge main
git push origin dev
```

Hoặc tạo Pull Request base = `dev`, compare = `main`.

> Nếu bỏ qua back-merge, bản sửa lỗi sẽ bị mất ở lần phát hành tiếp theo từ `dev`.

---

# 7. Phát hành (dev → main)

Khi `dev` đã ổn định và đủ tính năng cho một phiên bản:

1. Tạo Pull Request: **base = `main`**, compare = `dev`.
2. Cần ít nhất 1 người approve, CI/CD chạy thành công.
3. Merge vào `main`.
4. Gắn tag theo Semantic Versioning (`vMAJOR.MINOR.PATCH`):

```bash
git checkout main
git pull
git tag -a v1.2.0 -m "release: v1.2.0"
git push origin v1.2.0
```

| Thay đổi                         | Tăng phần |
| -------------------------------- | --------- |
| Thay đổi phá vỡ tương thích      | MAJOR     |
| Thêm tính năng (từ `dev`)        | MINOR     |
| Hotfix                           | PATCH     |

---

# 8. Quy định về Pull Request (PR)

## Khi nào tạo PR

Tạo Pull Request khi:

* Hoàn thành task.
* Code build thành công.
* Đã chạy test.
* Đã rebase với nhánh đích mới nhất (`dev`, hoặc `main` với hotfix).

---

## Nhánh đích của PR

| Nhánh nguồn                                  | Base (nhánh đích) |
| -------------------------------------------- | ----------------- |
| `feature/*`, `bugfix/*`, `docs/*`, `chore/*` | `dev`             |
| `hotfix/*`                                   | `main`            |
| `dev` (phát hành)                            | `main`            |
| `main` (back-merge sau hotfix)               | `dev`             |

---

## Kích thước Pull Request

Khuyến nghị:

```text
Dưới 300 dòng thay đổi
```

Cảnh báo:

```text
Trên 500 dòng thay đổi
```

PR càng nhỏ thì càng dễ review và ít lỗi.

---

## Điều kiện Merge

Một PR chỉ được merge khi:

* CI/CD chạy thành công.
* Có 1 approve của thành viên khác (không cần reviewer chỉ định).
* Mô tả PR có kết quả test/bench (khi chưa có CI).
* Không còn comment chưa xử lý.
* Không còn conflict với nhánh đích.

---

# 9. Quy chuẩn Commit

Dự án áp dụng chuẩn:

**Conventional Commits**

Định dạng:

```text
<type>(scope): mô tả
```

Ví dụ:

```text
feat(auth): add google login
fix(api): resolve token validation
refactor(user): simplify permission logic
test(auth): add jwt test cases
ci(github): add backend workflow
```

---

## Các loại commit

| Type     | Ý nghĩa           |
| -------- | ----------------- |
| feat     | Thêm tính năng    |
| fix      | Sửa lỗi           |
| refactor | Tái cấu trúc code |
| perf     | Tối ưu hiệu năng  |
| style    | Format code       |
| test     | Viết test         |
| docs     | Cập nhật tài liệu |
| ci       | CI/CD             |
| chore    | Công việc bảo trì |

---

## Quy tắc viết commit

Phải:

* Viết ở thì hiện tại.
* Bắt đầu bằng động từ.
* Không viết hoa chữ đầu.
* Không kết thúc bằng dấu chấm.
* Tiêu đề dưới 50 ký tự.

Ví dụ đúng:

```text
feat(auth): add login endpoint
```

Ví dụ sai:

```text
Added Login Endpoint.
```

---
