import ctypes, datetime as dt, hmac, json, secrets, socket, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8765
PIN = f"{secrets.randbelow(1000000):06d}"
TOKEN = secrets.token_urlsafe(32)
paired = False
attempts = 0

def admin():
    try: return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception: return False

def addresses():
    result=set()
    try:
        for item in socket.getaddrinfo(socket.gethostname(),None,socket.AF_INET):
            ip=item[4][0]
            if not ip.startswith("127."): result.add(ip)
    except OSError: pass
    return sorted(result)

class SYSTEMTIME(ctypes.Structure):
    _fields_=[("year",ctypes.c_ushort),("month",ctypes.c_ushort),
      ("dow",ctypes.c_ushort),("day",ctypes.c_ushort),("hour",ctypes.c_ushort),
      ("minute",ctypes.c_ushort),("second",ctypes.c_ushort),("ms",ctypes.c_ushort)]

def set_time(value):
    x=dt.datetime.fromisoformat(value)
    if x.tzinfo is not None: raise ValueError("Envie a data/hora sem fuso.")
    st=SYSTEMTIME(x.year,x.month,x.weekday(),x.day,x.hour,x.minute,x.second,0)
    if not ctypes.windll.kernel32.SetLocalTime(ctypes.byref(st)):
        raise ctypes.WinError()

class Handler(BaseHTTPRequestHandler):
    def send(self,code,obj):
        data=json.dumps(obj,ensure_ascii=False).encode()
        self.send_response(code);self.send_header("Content-Type","application/json; charset=utf-8")
        self.send_header("Content-Length",str(len(data)));self.send_header("Cache-Control","no-store")
        self.end_headers();self.wfile.write(data)
    def body(self):
        n=int(self.headers.get("Content-Length","0"))
        if n<1 or n>4096: raise ValueError("Solicitação inválida.")
        return json.loads(self.rfile.read(n))
    def auth(self):
        return paired and hmac.compare_digest(self.headers.get("Authorization",""),"Bearer "+TOKEN)
    def do_GET(self):
        if self.path=="/health": return self.send(200,{"ok":True})
        if self.path=="/time":
            if not self.auth(): return self.send(401,{"error":"Não autorizado."})
            now=dt.datetime.now().astimezone()
            return self.send(200,{"datetime":now.replace(tzinfo=None).isoformat(timespec="seconds"),"timezone":str(now.tzinfo)})
        self.send(404,{"error":"Rota não encontrada."})
    def do_POST(self):
        global paired,attempts
        try:
            data=self.body()
            if self.path=="/pair":
                if paired: return self.send(409,{"error":"Já pareado. Reinicie o servidor para novo PIN."})
                attempts+=1
                if attempts>5: return self.send(429,{"error":"Muitas tentativas. Reinicie o servidor."})
                if not hmac.compare_digest(str(data.get("pin","")),PIN): return self.send(403,{"error":"PIN incorreto."})
                paired=True
                return self.send(200,{"token":TOKEN})
            if self.path=="/set":
                if not self.auth(): return self.send(401,{"error":"Não autorizado."})
                if not admin(): return self.send(403,{"error":"Abra o servidor como administrador."})
                value=str(data.get("datetime",""))
                if len(value)!=19: return self.send(400,{"error":"Formato de data/hora inválido."})
                set_time(value)
                return self.send(200,{"ok":True,"datetime":value})
            self.send(404,{"error":"Rota não encontrada."})
        except Exception as e: self.send(400,{"error":str(e)})
    def log_message(self,fmt,*args): print("[HTTP]",fmt%args)

if __name__=="__main__":
    if sys.platform!="win32": raise SystemExit("Execute este servidor no Windows.")
    print("=== RELOGIO REMOTO ===")
    print("PIN:",PIN)
    print("Endereços do PC:")
    for ip in addresses(): print(f"  {ip}:{PORT}")
    print("Mantenha esta janela aberta. Ctrl+C para sair.")
    if not admin(): print("AVISO: abra como administrador para poder alterar o relógio.")
    ThreadingHTTPServer(("0.0.0.0",PORT),Handler).serve_forever()
