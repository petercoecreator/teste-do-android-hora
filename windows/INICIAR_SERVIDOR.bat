@echo off
title Relogio Remoto - Servidor
echo Este iniciador e uma alternativa para executar o servidor Python.
echo O Windows exibira uma solicitacao de administrador.
py -m pip install --user "qrcode[pil]"
py "%~dp0servidor.py"
if errorlevel 1 pause
