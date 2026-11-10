import ctypes
import datetime as dt
import hmac
import json
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QPixmap, QImage, QAction, QIcon
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QLabel, QPushButton, QVBoxLayout,
    QHBoxLayout, QFrame, QSystemTrayIcon, QMenu, QMessageBox, QCheckBox, QStyle
)

PORT = 8765
PIN = f"{secrets.randbelow(1_000_000):06d}"
TOKEN = secrets.token_urlsafe(32)
paired = False
attempts = 0
pair_lock = threading.Lock()
http_server = None


def is_admin():
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def relaunch_as_admin():
    if is_admin():
        return True
    if getattr(sys, "frozen", False):
        executable, arguments = sys.executable, sys.argv[1:]
    else:
        executable, arguments = sys.executable, [str(Path(__file__).resolve()), *sys.argv[1:]]
    result = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", executable, subprocess.list2cmdline(arguments), None, 1
    )
    if result <= 32:
        ctypes.windll.user32.MessageBoxW(
            None, "O Relógio Remoto precisa de permissão de administrador.",
            "Relógio Remoto", 0x10
        )
    return False


def local_ip():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 80))
        address = sock.getsockname()[0]
        if not address.startswith("127."):
            return address
    except OSError:
        pass
    finally:
        sock.close()
    try:
        for item in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            address = item[4][0]
            if not address.startswith("127."):
                return address
    except OSError:
        pass
    return None


class SYSTEMTIME(ctypes.Structure):
    _fields_ = [
        ("year", ctypes.c_ushort), ("month", ctypes.c_ushort),
        ("dow", ctypes.c_ushort), ("day", ctypes.c_ushort),
        ("hour", ctypes.c_ushort), ("minute", ctypes.c_ushort),
        ("second", ctypes.c_ushort), ("ms", ctypes.c_ushort)
    ]


def set_windows_time(value):
    parsed = dt.datetime.fromisoformat(value)
    if parsed.tzinfo is not None:
        raise ValueError("Envie a data e hora sem informação de fuso horário.")
    system_time = SYSTEMTIME(
        parsed.year, parsed.month, parsed.weekday(), parsed.day,
        parsed.hour, parsed.minute, parsed.second, 0
    )
    if not ctypes.windll.kernel32.SetLocalTime(ctypes.byref(system_time)):
        raise ctypes.WinError()


class Handler(BaseHTTPRequestHandler):
    server_version = "RelogioRemoto/2"

    def send_json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        size = int(self.headers.get("Content-Length", "0"))
        if size < 1 or size > 4096:
            raise ValueError("Solicitação vazia ou muito grande.")
        data = json.loads(self.rfile.read(size).decode("utf-8"))
        if not isinstance(data, dict):
            raise ValueError("Formato JSON inválido.")
        return data

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        return paired and hmac.compare_digest(supplied, "Bearer " + TOKEN)

    def do_GET(self):
        route = urlsplit(self.path).path
        if route == "/health":
            return self.send_json(200, {"ok": True, "app": "RelogioRemoto"})
        if route == "/time":
            if not self.authorized():
                return self.send_json(401, {"error": "Conexão não autorizada. Faça a leitura do QR Code novamente."})
            now = dt.datetime.now().astimezone()
            return self.send_json(200, {
                "datetime": now.replace(tzinfo=None).isoformat(timespec="seconds"),
                "timezone": str(now.tzinfo)
            })
        return self.send_json(404, {"error": "Rota não encontrada."})

    def do_POST(self):
        global paired, attempts
        route = urlsplit(self.path).path
        try:
            data = self.read_json()
            if route == "/pair":
                with pair_lock:
                    if paired:
                        return self.send_json(409, {"error": "Este servidor já foi conectado. Gere um novo QR Code no aplicativo."})
                    attempts += 1
                    if attempts > 5:
                        return self.send_json(429, {"error": "Limite de tentativas atingido. Gere um novo QR Code."})
                    if not hmac.compare_digest(str(data.get("pin", "")), PIN):
                        return self.send_json(403, {"error": "PIN incorreto. Confira o QR Code do computador."})
                    paired = True
                return self.send_json(200, {"token": TOKEN})
            if route == "/set":
                if not self.authorized():
                    return self.send_json(401, {"error": "Não autorizado. Conecte novamente pelo QR Code."})
                if not is_admin():
                    return self.send_json(403, {"error": "O servidor não está executando como administrador."})
                value = str(data.get("datetime", ""))
                if len(value) != 19:
                    return self.send_json(400, {"error": "Formato de data/hora inválido."})
                set_windows_time(value)
                return self.send_json(200, {"ok": True, "datetime": value})
            return self.send_json(404, {"error": "Rota não encontrada."})
        except (ValueError, json.JSONDecodeError) as exc:
            return self.send_json(400, {"error": str(exc)})
        except Exception as exc:
            return self.send_json(500, {"error": str(exc)})

    def log_message(self, fmt, *args):
        print("[HTTP]", fmt % args)


class LocalServer(ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True


def scheduled_task_exists():
    result = subprocess.run(
        ["schtasks", "/Query", "/TN", "Relogio Remoto"],
        capture_output=True, text=True, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
    )
    return result.returncode == 0


def set_startup(enabled):
    if getattr(sys, "frozen", False):
        command = f'"{sys.executable}" --background'
    else:
        command = f'"{sys.executable}" "{Path(__file__).resolve()}" --background'
    if enabled:
        args = ["schtasks", "/Create", "/TN", "Relogio Remoto", "/SC", "ONLOGON",
                "/TR", command, "/RL", "HIGHEST", "/F"]
    else:
        args = ["schtasks", "/Delete", "/TN", "Relogio Remoto", "/F"]
    result = subprocess.run(args, capture_output=True, text=True,
                            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout or "Não foi possível alterar a inicialização automática.").strip())


def make_qr_pixmap(host):
    import qrcode
    from PIL.ImageQt import ImageQt
    payload = {"app": "RelogioRemoto", "version": 1, "host": host, "port": PORT, "pin": PIN}
    image = qrcode.make(json.dumps(payload, separators=(",", ":"))).convert("RGB")
    return QPixmap.fromImage(ImageQt(image))


class MainWindow(QMainWindow):
    def __init__(self, host):
        super().__init__()
        self.host = host
        self.allow_close = False
        self.setWindowTitle("Relógio Remoto")
        self.setFixedSize(540, 690)
        self.setStyleSheet("""
            QMainWindow, QWidget { background: #0b1220; color: #edf4ff; font-family: Segoe UI; }
            QFrame#panel { background: #152238; border: 1px solid #263a58; border-radius: 16px; }
            QLabel#title { font-size: 25px; font-weight: 700; }
            QLabel#muted { color: #9db0ca; font-size: 12px; }
            QLabel#value { color: #ffffff; font-size: 20px; font-weight: 700; }
            QPushButton { background: #2878e8; color: white; border: 0; border-radius: 10px; padding: 11px 16px; font-weight: 600; }
            QPushButton:hover { background: #3989f5; }
            QPushButton#secondary { background: #263a58; }
            QCheckBox { color: #dce8f8; spacing: 8px; }
        """)
        root = QWidget()
        self.setCentralWidget(root)
        layout = QVBoxLayout(root)
        layout.setContentsMargins(24, 22, 24, 20)
        layout.setSpacing(13)

        title = QLabel("Relógio Remoto")
        title.setObjectName("title")
        layout.addWidget(title)
        subtitle = QLabel("SERVIDOR DO WINDOWS  •  CONEXÃO LOCAL SEGURA")
        subtitle.setObjectName("muted")
        layout.addWidget(subtitle)

        panel = QFrame()
        panel.setObjectName("panel")
        box = QVBoxLayout(panel)
        box.setContentsMargins(18, 16, 18, 16)
        box.setSpacing(8)
        self.status = QLabel("● SERVIDOR ATIVO — aguardando o celular")
        self.status.setStyleSheet("color:#55d6a3;font-weight:700;")
        box.addWidget(self.status)
        ip_title = QLabel("ENDEREÇO DO COMPUTADOR")
        ip_title.setObjectName("muted")
        box.addWidget(ip_title)
        self.ip_label = QLabel(f"{host or 'Não identificado'}:{PORT}")
        self.ip_label.setObjectName("value")
        box.addWidget(self.ip_label)
        pin_title = QLabel("PIN DE PAREAMENTO")
        pin_title.setObjectName("muted")
        box.addWidget(pin_title)
        self.pin_label = QLabel(PIN)
        self.pin_label.setObjectName("value")
        self.pin_label.setStyleSheet("color:#65b7ff;font-size:28px;letter-spacing:5px;")
        box.addWidget(self.pin_label)
        row = QHBoxLayout()
        copy_ip = QPushButton("Copiar endereço")
        copy_ip.setObjectName("secondary")
        copy_ip.clicked.connect(lambda: QApplication.clipboard().setText(f"{host}:{PORT}"))
        copy_pin = QPushButton("Copiar PIN")
        copy_pin.setObjectName("secondary")
        copy_pin.clicked.connect(lambda: QApplication.clipboard().setText(PIN))
        row.addWidget(copy_ip)
        row.addWidget(copy_pin)
        box.addLayout(row)
        layout.addWidget(panel)

        qr_panel = QFrame()
        qr_panel.setObjectName("panel")
        qr_box = QVBoxLayout(qr_panel)
        qr_box.setContentsMargins(12, 12, 12, 12)
        qr_box.setAlignment(Qt.AlignCenter)
        self.qr = QLabel()
        self.qr.setAlignment(Qt.AlignCenter)
        self.qr.setPixmap(make_qr_pixmap(host).scaled(210, 210, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        qr_box.addWidget(self.qr)
        qr_caption = QLabel("Abra o app no celular e leia este QR Code")
        qr_caption.setObjectName("muted")
        qr_caption.setAlignment(Qt.AlignCenter)
        qr_box.addWidget(qr_caption)
        layout.addWidget(qr_panel)

        self.startup = QCheckBox("Iniciar automaticamente quando eu entrar no Windows")
        self.startup.setChecked(scheduled_task_exists())
        self.startup.toggled.connect(self.toggle_startup)
        layout.addWidget(self.startup)
        buttons = QHBoxLayout()
        regenerate = QPushButton("Gerar novo QR Code")
        regenerate.setObjectName("secondary")
        regenerate.clicked.connect(self.regenerate)
        hide = QPushButton("Minimizar para bandeja")
        hide.clicked.connect(self.hide)
        buttons.addWidget(regenerate)
        buttons.addWidget(hide)
        layout.addLayout(buttons)
        foot = QLabel("O servidor permanece ativo na bandeja do sistema. Não encaminhe a porta 8765 para a internet.")
        foot.setObjectName("muted")
        foot.setWordWrap(True)
        layout.addWidget(foot)

        self.tray = QSystemTrayIcon(self)
        self.tray.setToolTip("Relógio Remoto — servidor ativo")
        self.tray.setIcon(QApplication.style().standardIcon(QStyle.StandardPixmap.SP_ComputerIcon))
        menu = QMenu()
        open_action = QAction("Abrir Relógio Remoto", self)
        open_action.triggered.connect(self.showNormal)
        new_action = QAction("Gerar novo QR Code", self)
        new_action.triggered.connect(self.regenerate)
        quit_action = QAction("Encerrar servidor", self)
        quit_action.triggered.connect(self.quit_app)
        menu.addAction(open_action)
        menu.addAction(new_action)
        menu.addSeparator()
        menu.addAction(quit_action)
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(lambda reason: self.showNormal() if reason == QSystemTrayIcon.DoubleClick else None)
        self.tray.show()

    def toggle_startup(self, enabled):
        try:
            set_startup(enabled)
        except Exception as exc:
            self.startup.blockSignals(True)
            self.startup.setChecked(not enabled)
            self.startup.blockSignals(False)
            QMessageBox.warning(self, "Inicialização automática", str(exc))

    def regenerate(self):
        global PIN, TOKEN, paired, attempts
        with pair_lock:
            PIN = f"{secrets.randbelow(1_000_000):06d}"
            TOKEN = secrets.token_urlsafe(32)
            paired = False
            attempts = 0
        self.pin_label.setText(PIN)
        self.qr.setPixmap(make_qr_pixmap(self.host).scaled(210, 210, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        self.status.setText("● Novo QR Code pronto — aguardando o celular")
        self.tray.showMessage("Relógio Remoto", "Um novo QR Code foi gerado.", QSystemTrayIcon.Information, 2500)

    def closeEvent(self, event):
        if self.allow_close:
            event.accept()
        else:
            event.ignore()
            self.hide()
            self.tray.showMessage("Relógio Remoto", "O servidor continua ativo em segundo plano.", QSystemTrayIcon.Information, 2200)

    def quit_app(self):
        self.allow_close = True
        self.tray.hide()
        QApplication.quit()


def main():
    global http_server
    if sys.platform != "win32":
        raise SystemExit("Este aplicativo deve ser executado no Windows.")
    if not relaunch_as_admin():
        return
    host = local_ip()
    try:
        http_server = LocalServer(("0.0.0.0", PORT), Handler)
    except OSError as exc:
        ctypes.windll.user32.MessageBoxW(
            None, f"Não foi possível iniciar o servidor na porta {PORT}: {exc}",
            "Relógio Remoto", 0x10
        )
        return
    thread = threading.Thread(target=http_server.serve_forever, daemon=True)
    thread.start()

    app = QApplication(sys.argv)
    app.setApplicationName("Relógio Remoto")
    app.setQuitOnLastWindowClosed(False)
    window = MainWindow(host)
    if "--background" not in sys.argv:
        window.show()
    app.aboutToQuit.connect(http_server.shutdown)
    app.aboutToQuit.connect(http_server.server_close)
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
