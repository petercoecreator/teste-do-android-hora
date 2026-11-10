# Relógio Remoto 2.0

Aplicativo Android para consultar e ajustar a data e a hora de um computador Windows na mesma rede Wi-Fi.

## Downloads

Quando a compilação automática terminar, abra a página Releases e baixe:
- app-debug.apk — instale no celular Android.
- RelogioRemoto-Servidor.exe — execute no computador Windows.

Downloads: https://github.com/petercoecreator/teste-do-android-hora/releases/latest

## Como conectar

1. No Windows, execute RelogioRemoto-Servidor.exe.
2. Confirme a solicitação de administrador do Windows.
3. O servidor mostra o IP e o PIN e abre a imagem do QR Code.
4. No Android, abra o app e toque em LER QR CODE DO COMPUTADOR.
5. Aponte a câmera para o QR Code.
6. Depois de conectado, consulte o horário ou escolha uma nova data e hora.
7. Para alterar o relógio, toque em APLICAR NO PC e confirme.

O computador e o celular precisam estar na mesma rede Wi-Fi. Mantenha o servidor aberto durante o uso. Reinicie o servidor para gerar outro PIN e permitir um novo pareamento.

## Segurança e limitações

- A conexão usa HTTP e foi projetada somente para uma rede local privada e confiável.
- Não encaminhe a porta TCP 8765 para a internet.
- O PIN é temporário e o servidor aceita um pareamento por execução.
- O executável solicita elevação pelo UAC do Windows; não tenta contornar a confirmação.
- Alterar o relógio pode afetar certificados, autenticação, jogos, atualizações e outros programas.
- O app não altera o fuso horário nem configura a sincronização automática do Windows.

## Código-fonte

- Android: pasta android/
- Servidor Windows: windows/servidor.py
- Compilação: .github/workflows/build-apk.yml
