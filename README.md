# Relógio Remoto

Aplicativo Android para consultar e alterar manualmente a data e a hora de um PC Windows na mesma rede Wi-Fi.

## Gerar APK
Abra a aba **Actions**, selecione **Gerar APK Android** e clique em **Run workflow**. Ao terminar, baixe o artefato **RelogioRemoto-APK**. Dentro do ZIP estará o arquivo `app-debug.apk`.

## Servidor Windows
Veja `windows/LEIA-ME.txt`. O servidor precisa ser executado como administrador para alterar a hora.

## Segurança
Esta versão inicial funciona apenas na rede local. Use uma rede Wi-Fi confiável e nunca exponha a porta 8765 à internet. O servidor usa HTTP local e PIN temporário; encerre-o quando não estiver usando. Alterar o relógio pode afetar certificados, autenticação, jogos e atualizações do Windows.
