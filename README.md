# Relógio Remoto

Aplicativos Android e Windows para consultar e ajustar a data e a hora do computador na mesma rede local.

## Windows
Baixe e execute `RelogioRemoto-Setup.exe`. O instalador cria atalhos no menu Iniciar e, opcionalmente, na área de trabalho. Pode configurar a inicialização automática. O aplicativo permanece na bandeja do sistema quando a janela é fechada.

O servidor usa a porta TCP 8765 e gera QR Code, IP e PIN a cada inicialização ou quando solicitado no aplicativo.

## Android
Instale `app-debug.apk`, permita o acesso à câmera quando solicitado e leia o QR Code exibido no Windows.

## Segurança
A conexão HTTP foi projetada somente para uma rede local privada e confiável. Não encaminhe a porta 8765 para a internet. Alterar o relógio pode afetar certificados, autenticação, jogos e atualizações.


O leitor QR do Android solicita a permissão da câmera na primeira utilização.
