# -*- coding: utf-8 -*-
"""
모바일티머니 자동출석 안드로이드 앱 - GitHub Actions 자동 APK 빌드 도우미
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
        self.root.title("📱 모바일티머니 자동출석 APK 자동 빌드 도우미")
        self.root.geometry("700x560")
        self.root.configure(bg="#1A1B1E")

        self.var_repo_url = tk.StringVar(value="")
        self._build_ui()
        self._load_existing_remote()

    def _build_ui(self) -> None:
        header = tk.Frame(self.root, bg="#6200EE", pady=14, padx=16)
        header.pack(fill=tk.X)
        tk.Label(
            header,
            text="📱 모바일티머니 자동출석 APK 자동 빌더",
            font=("맑은 고딕", 13, "bold"),
            fg="#FFFFFF",
            bg="#6200EE",
        ).pack(anchor="w")
        tk.Label(
            header,
            text="Android Studio 없이 GitHub 클라우드 서버에서 APK를 1~2분 만에 자동 빌드합니다.",
            font=("맑은 고딕", 9),
            fg="#E2E8F0",
            bg="#6200EE",
        ).pack(anchor="w", pady=(2, 0))

        body = tk.Frame(self.root, bg="#1A1B1E", padx=16, pady=14)
        body.pack(fill=tk.BOTH, expand=True)

        step1 = tk.LabelFrame(
            body,
            text=" 1단계: GitHub 저장소(Repository) 준비 ",
            fg="#BB86FC",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=10,
            pady=8
        )
        step1.pack(fill=tk.X, pady=(0, 10))

        tk.Label(
            step1,
            text="GitHub에 새 빈 저장소(예: tmoney-auto-app)를 만든 후 주소를 아래에 붙여넣으세요.",
            font=("맑은 고딕", 9),
            fg="#E6E6E6",
            bg="#202225",
            anchor="w",
        ).pack(side=tk.LEFT, fill=tk.X, expand=True)

        tk.Button(
            step1,
            text="🌐 GitHub 새 저장소 만들기",
            font=("맑은 고딕", 9, "bold"),
            bg="#303237",
            fg="#FFFFFF",
            command=lambda: webbrowser.open("https://github.com/new"),
        ).pack(side=tk.RIGHT)

        step2 = tk.LabelFrame(
            body,
            text=" 2단계: GitHub 저장소 주소 입력 ",
            fg="#BB86FC",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=10,
            pady=8
        )
        step2.pack(fill=tk.X, pady=(0, 10))

        entry = tk.Entry(
            step2,
            textvariable=self.var_repo_url,
            font=("Consolas", 10),
            bg="#121214",
            fg="#00E676",
            insertbackground="#00E676",
        )
        entry.pack(fill=tk.X, pady=4)

        self.btn_build = tk.Button(
            body,
            text="🚀 GitHub로 코드 업로드 및 APK 자동 빌드 시작",
            font=("맑은 고딕", 11, "bold"),
            bg="#6200EE",
            fg="#FFFFFF",
            pady=8,
            command=self._start_build_process,
        )
        self.btn_build.pack(fill=tk.X, pady=(0, 10))

        log_frame = tk.LabelFrame(
            body,
            text=" 진행 상황 로그 ",
            fg="#BB86FC",
            bg="#202225",
            font=("맑은 고딕", 9, "bold"),
            padx=8,
            pady=6
        )
        log_frame.pack(fill=tk.BOTH, expand=True)

        self.log_text = tk.Text(
            log_frame,
            bg="#121214",
            fg="#D1D5DB",
            font=("Consolas", 9),
            wrap=tk.WORD,
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)

    def _log(self, text: str) -> None:
        self.log_text.insert(tk.END, text + "\n")
        self.log_text.see(tk.END)

    def _load_existing_remote(self) -> None:
        try:
            res = subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "remote", "get-url", "origin"], capture_output=True, text=True)
            url = res.stdout.strip()
            if url:
                self.var_repo_url.set(url)
                self._log(f"기존 연결된 GitHub 저장소: {url}")
        except Exception:
            pass

    def _start_build_process(self) -> None:
        url = self.var_repo_url.get().strip()
        if not url:
            messagebox.showwarning("입력 필요", "GitHub 저장소 주소를 입력해주세요.")
            return

        self.btn_build.config(state=tk.DISABLED)
        threading.Thread(target=self._run_git_push, args=(url,), daemon=True).start()

    def _run_git_push(self, url: str) -> None:
        try:
            self._log("▶ 1. Git 저장소 초기화 중...")
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "init"], capture_output=True)
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "branch", "-M", "main"], capture_output=True)

            self._log("▶ 2. 원격 저장소 설정 중...")
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "remote", "remove", "origin"], capture_output=True)
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "remote", "add", "origin", url], capture_output=True)

            self._log("▶ 3. 파일 스테이징 및 커밋 생성 중...")
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "add", "."], capture_output=True)
            subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "commit", "-m", "Initial commit: Tmoney Auto Attendance App"], capture_output=True)

            self._log("▶ 4. GitHub로 푸시 중 (잠시 기다려주세요)...")
            p = subprocess.run([GIT_CMD, "-C", str(WORKSPACE_DIR), "push", "-u", "origin", "main", "--force"], capture_output=True, text=True)

            if p.returncode == 0:
                self._log("✅ 코드 업로드 성공! GitHub Actions에서 APK 빌드가 자동 시작되었습니다.")
                actions_url = url.rstrip(".git") + "/actions"
                self._log(f"🔗 빌드 진행 확인: {actions_url}")
                webbrowser.open(actions_url)
                messagebox.showinfo("성공", "GitHub Actions에서 APK 생성이 시작되었습니다!\n1~2분 후 Actions 탭에서 완성된 APK를 다운로드할 수 있습니다.")
            else:
                self._log(f"❌ 푸시 실패: {p.stderr}")
                messagebox.showerror("푸시 실패", f"GitHub 업로드에 실패했습니다:\n{p.stderr}")
        except Exception as e:
            self._log(f"❌ 오류: {str(e)}")
            messagebox.showerror("오류", str(e))
        finally:
            self.btn_build.config(state=tk.NORMAL)

if __name__ == "__main__":
    enable_high_dpi()
    root = tk.Tk()
    app = ApkBuilderApp(root)
    root.mainloop()
