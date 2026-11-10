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

PORT = 8765
PIN = f"{secrets.randbelow(1_000_000):06d}"
TOKEN = secrets.token_urlsafe(32)
paired = False
attempts = 0
pair_lock = threading.Lock()


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
        executable = sys.executable
        arguments = [str(Path(__file__).resolve()), *sys.argv[1:]]
    result = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", executable, subprocess.list2cmdline(arguments), None, 1
    )
    if result <= 32:
        ctypes.windll.user32.MessageBoxW(
            None,
            "O Relógio Remoto precisa de permissão de administrador para alterar a hora do Windows.",
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


def make_qr(host):
    payload = {
        "app": "RelogioRemoto", "version": 1,
        "host": host, "port": PORT, "pin": PIN
    }
    try:
        import qrcode
        image = qrcode.make(json.dumps(payload, separators=(",", ":")))
        try:
            folder = Path(sys.executable).resolve().parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent
            output = folder / "conexao_relogio_remoto.png"
            image.save(output)
        except OSError:
            output = Path(tempfile.gettempdir()) / "conexao_relogio_remoto.png"
            image.save(output)
        print(f"\nQR Code salvo em: {output}")
        try:
            os.startfile(str(output))
        except OSError:
            print("Abra manualmente o arquivo PNG para escanear o código.")
    except Exception as exc:
        print(f"\nNão foi possível gerar o QR Code: {exc}")
        print("Use o IP e o PIN exibidos para conectar manualmente.")


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
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ValueError("Tamanho da solicitação inválido.")
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
                        return self.send_json(409, {"error": "Este servidor já foi conectado. Feche e abra novamente para gerar outro QR Code."})
                    attempts += 1
                    if attempts > 5:
                        return self.send_json(429, {"error": "Limite de tentativas atingido. Reinicie o servidor."})
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


def main():
    if sys.platform != "win32":
        raise SystemExit("Este servidor deve ser executado no Windows.")
    if not relaunch_as_admin():
        return

    host = local_ip()
    print("=" * 48)
    print("          RELOGIO REMOTO - SERVIDOR")
    print("=" * 48)
    print(f"PIN DE CONEXÃO: {PIN}")
    if host:
        print(f"IP DESTE COMPUTADOR: {host}")
        print(f"ENDEREÇO: http://{host}:{PORT}")
        make_qr(host)
    else:
        print("Não foi possível identificar o IP da rede.")
        print("Verifique se o computador está conectado ao Wi-Fi.")

    print("\nMantenha esta janela aberta durante o uso.")
    print("No celular, abra o app e toque em LER QR CODE.")
    print("Use somente em uma rede Wi-Fi privada e confiável.")
    print("Não encaminhe a porta 8765 para a internet.")
    print("\nAguardando conexão... (Ctrl+C para encerrar)\n")
    try:
        with LocalServer(("0.0.0.0", PORT), Handler) as server:
            server.serve_forever()
    except OSError as exc:
        print(f"Não foi possível iniciar o servidor na porta {PORT}: {exc}")
        input("Pressione Enter para fechar...")


if __name__ == "__main__":
    main()
