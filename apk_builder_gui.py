# -*- coding: utf-8 -*-
"""
KTX / SRT 자동예매 안드로이드 앱 - GitHub Actions 자동 APK 빌드 도우미
"""

from __future__ import annotations

import ctypes
import os
import shutil
import subprocess
import threading
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import messagebox, ttk

WORKSPACE_DIR = Path(__file__).resolve().parent

# Git 실행 파일 경로 탐색
def find_git_cmd() -> str:
    candidates = [
        "git",
        r"C:\Program Files\Git\cmd\git.exe",
        r"C:\Program Files\Git\bin\git.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\Git\cmd\git.exe"),
    ]
    for c in candidates:
        if shutil.which(c):
            return c
    return "git"

GIT_CMD = find_git_cmd()


def enable_high_dpi() -> None:
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass


class ApkBuilderApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("📱 KTX/SRT 자동예매 안드로이드 APK 자동 빌드 도우미")
        self.root.geometry("700x560")
        self.root.configure(bg="#1A1B1E")

        self.var_repo_url = tk.StringVar(value="")
        self._build_ui()
        self._load_existing_remote()

    def _build_ui(self) -> None:
        header = tk.Frame(self.root, bg="#1F6FEB", pady=14, padx=16)
        header.pack(fill=tk.X)
        tk.Label(
            header,
            text="📱 KTX/SRT 자동로그인 안드로이드 APK 자동 생성기",
            font=("맑은 고딕", 13, "bold"),
            fg="#FFFFFF",
            bg="#1F6FEB",
        ).pack(anchor="w")
        tk.Label(
            header,
            text="Android Studio 설치 없이 GitHub 클라우드 서버에서 APK 설치 파일을 1~2분 만에 자동 생성합니다.",
            font=("맑은 고딕", 9),
            fg="#E2E8F0",
            bg="#1F6FEB",
        ).pack(anchor="w", pady=(2, 0))

        body = tk.Frame(self.root, bg="#1A1B1E", padx=16, pady=14)
        body.pack(fill=tk.BOTH, expand=True)

        # 1단계
        step1 = tk.LabelFrame(
            body,
            text=" 1단계: GitHub 빈 저장소(Repository) 준비 ",
            fg="#58A6E6",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=10,
            pady=8
        )
        step1.pack(fill=tk.X, pady=(0, 10))

        tk.Label(
            step1,
            text="GitHub에 새 빈 저장소(예: ktx-srt-app)를 만든 후 주소를 아래 2단계에 붙여넣으세요.",
            font=("맑은 고딕", 9),
            fg="#E6E6E6",
            bg="#202225",
            anchor="w",
        ).pack(side=tk.LEFT, fill=tk.X, expand=True)

        tk.Button(
            step1,
            text="🌐 GitHub 새 저장소 만들기 열기",
            font=("맑은 고딕", 9, "bold"),
            bg="#303237",
            fg="#FFFFFF",
            relief=tk.FLAT,
            padx=10,
            pady=4,
            cursor="hand2",
            command=lambda: webbrowser.open("https://github.com/new"),
        ).pack(side=tk.RIGHT, padx=(8, 0))

        # 2단계
        step2 = tk.LabelFrame(
            body,
            text=" 2단계: 저장소 주소(URL) 입력 및 빌드 시작 ",
            fg="#58A6E6",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=10,
            pady=8
        )
        step2.pack(fill=tk.X, pady=(0, 10))

        url_row = tk.Frame(step2, bg="#202225")
        url_row.pack(fill=tk.X, pady=4)
        tk.Label(
            url_row,
            text="저장소 주소:",
            font=("맑은 고딕", 10, "bold"),
            fg="#E6E6E6",
            bg="#202225"
        ).pack(side=tk.LEFT, padx=(0, 8))

        ent = tk.Entry(
            url_row,
            textvariable=self.var_repo_url,
            font=("맑은 고딕", 10),
            bg="#15161A",
            fg="#E6E6E6",
            insertbackground="#FFFFFF"
        )
        ent.pack(side=tk.LEFT, fill=tk.X, expand=True)

        tk.Label(
            step2,
            text="입력 예시: https://github.com/내계정/ktx-srt-app.git",
            font=("맑은 고딕", 8),
            fg="#888888",
            bg="#202225",
        ).pack(anchor="w", pady=(2, 8))

        btn_row = tk.Frame(step2, bg="#202225")
        btn_row.pack(fill=tk.X)

        self.btn_upload = tk.Button(
            btn_row,
            text="🚀 GitHub에 자동 업로드 & APK 빌드 시작하기",
            font=("맑은 고딕", 10, "bold"),
            bg="#16A34A",
            fg="#FFFFFF",
            activebackground="#15803D",
            activeforeground="#FFFFFF",
            relief=tk.FLAT,
            pady=8,
            cursor="hand2",
            command=self.on_click_upload,
        )
        self.btn_upload.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 8))

        self.btn_open_actions = tk.Button(
            btn_row,
            text="📦 GitHub Actions (APK 다운로드 페이지) 열기",
            font=("맑은 고딕", 10, "bold"),
            bg="#1F6FEB",
            fg="#FFFFFF",
            activebackground="#1158C7",
            activeforeground="#FFFFFF",
            relief=tk.FLAT,
            padx=14,
            pady=8,
            cursor="hand2",
            command=self.on_click_open_actions,
        )
        self.btn_open_actions.pack(side=tk.RIGHT)

        # 로그창
        log_frame = tk.LabelFrame(
            body,
            text=" 📋 빌드 진행 로그 ",
            fg="#58A6E6",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=8,
            pady=6
        )
        log_frame.pack(fill=tk.BOTH, expand=True)

        self.txt_log = tk.Text(
            log_frame,
            font=("Consolas", 9),
            bg="#15161A",
            fg="#D1D5DB",
            wrap=tk.WORD,
            relief=tk.FLAT
        )
        self.txt_log.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scroll = ttk.Scrollbar(log_frame, command=self.txt_log.yview)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)
        self.txt_log.configure(yscrollcommand=scroll.set)

        self.log(f"준비 완료! (작업 폴더: {WORKSPACE_DIR})")
        self.log(f"Git 도구: {GIT_CMD}")

    def log(self, text: str) -> None:
        self.txt_log.insert(tk.END, text + "\n")
        self.txt_log.see(tk.END)

    def _run_git(self, args: list[str]) -> tuple[int, str]:
        proc = subprocess.run(
            [GIT_CMD, *args],
            cwd=str(WORKSPACE_DIR),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        output = (proc.stdout or "") + (proc.stderr or "")
        return proc.returncode, output.strip()

    def _load_existing_remote(self) -> None:
        try:
            code, out = self._run_git(["remote", "get-url", "origin"])
            if code == 0 and out.startswith("http"):
                self.var_repo_url.set(out.strip())
        except Exception:
            pass

    def on_click_open_actions(self) -> None:
        url = self.var_repo_url.get().strip()
        if not url.startswith("http"):
            messagebox.showinfo("안내", "먼저 GitHub 저장소 주소를 입력해주세요.")
            return
        clean_url = url[:-4] if url.endswith(".git") else url.rstrip("/")
        webbrowser.open(f"{clean_url}/actions")

    def on_click_upload(self) -> None:
        repo_url = self.var_repo_url.get().strip()
        if not repo_url.startswith("http"):
            messagebox.showwarning(
                "주소 확인",
                "올바른 GitHub 저장소 주소(https://github.com/...)를 입력해주세요!",
            )
            return

        self.btn_upload.configure(state=tk.DISABLED, text="⏳ 업로드 진행 중...")

        def _worker() -> None:
            try:
                self.root.after(0, lambda: self.log("-----------------------------------------"))
                self.root.after(0, lambda: self.log("1. Git 로컬 저장소 초기화 및 파일 커밋 중..."))

                if not (WORKSPACE_DIR / ".git").exists():
                    self._run_git(["init"])

                # 커밋 사용자 정보
                code_name, out_name = self._run_git(["config", "user.name"])
                if code_name != 0 or not out_name:
                    self._run_git(["config", "user.name", "KtxSrtAppBuilder"])
                code_email, out_email = self._run_git(["config", "user.email"])
                if code_email != 0 or not out_email:
                    self._run_git(["config", "user.email", "builder@example.com"])

                self._run_git(["branch", "-M", "main"])
                self._run_git(["add", "."])
                _, commit_out = self._run_git(["commit", "-m", "Build KTX-SRT Auto Login Android APK"])
                if commit_out:
                    self.root.after(0, lambda: self.log(commit_out))

                self.root.after(0, lambda: self.log(f"2. 원격 저장소 연결: {repo_url}"))
                self._run_git(["remote", "remove", "origin"])
                self._run_git(["remote", "add", "origin", repo_url])

                self.root.after(0, lambda: self.log("3. GitHub 클라우드로 소스코드 푸시 중... (인증창이 뜨면 로그인해주세요)"))
                code, push_out = self._run_git(["push", "-u", "origin", "main", "--force"])
                if push_out:
                    self.root.after(0, lambda: self.log(push_out))

                if code == 0:
                    self.root.after(0, self._on_upload_success)
                else:
                    self.root.after(0, lambda: self._on_upload_error(push_out))
            except Exception as exc:
                self.root.after(0, lambda: self._on_upload_error(str(exc)))

        threading.Thread(target=_worker, daemon=True).start()

    def _on_upload_success(self) -> None:
        self.btn_upload.configure(state=tk.NORMAL, text="🚀 GitHub에 자동 업로드 & APK 빌드 시작하기")
        self.log("✅ GitHub 업로드 성공! GitHub Actions에서 클라우드 APK 빌드가 시작되었습니다.")
        if messagebox.askyesno(
            "업로드 성공! 🎉",
            "GitHub에 앱 소스가 성공적으로 업로드되어 자동 APK 빌드가 시작되었습니다!\n\n"
            "약 1~2분 뒤 Actions 페이지에서 완성된 APK(KtxSrtAuto-Debug-APK)를 다운로드할 수 있습니다.\n\n"
            "지금 바로 GitHub Actions 페이지를 열어 확인하시겠습니까?",
        ):
            self.on_click_open_actions()

    def _on_upload_error(self, err: str) -> None:
        self.btn_upload.configure(state=tk.NORMAL, text="🚀 GitHub에 자동 업로드 & APK 빌드 시작하기")
        self.log(f"❌ 업로드 중 오류 발생:\n{err}")
        messagebox.showerror(
            "업로드 실패",
            f"GitHub 업로드 중 오류가 발생했습니다.\n저장소 주소 및 권한을 확인해주세요.\n\n{err[:300]}",
        )


def main() -> None:
    enable_high_dpi()
    root = tk.Tk()
    ApkBuilderApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
