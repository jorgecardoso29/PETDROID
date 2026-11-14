# Pet para Android (APK)

Um robozinho que anda pela tela por cima dos outros apps.

## Gerar o APK sem instalar nada (GitHub)
1. Crie uma conta gratuita em github.com e um repositório novo (pode ser privado).
2. Envie todos os arquivos desta pasta, **incluindo a pasta oculta `.github`**
   (por "Add file > Upload files" ou, se ela não subir, crie o arquivo
   `.github/workflows/apk.yml` em "Add file > Create new file" e cole o conteúdo).
3. Abra a aba **Actions**, espere o "Gerar APK" ficar verde (uns 3-5 min).
4. Entre na execução e baixe o arquivo **pet-apk** (vem em .zip, dentro tem o app-debug.apk).

## Instalar
1. Passe o APK para o celular e abra (permita "instalar apps desconhecidos" se pedir).
2. Abra o app **Pet** e toque em "1. Permitir exibir sobre outros apps", ative para o Pet.
3. Volte e toque em "2. Ligar o pet". Para desligar: botão "Desligar o pet" ou "Parar" na notificação.

## Como usar
- Ele anda, pisca, muda de humor e dorme sozinho (acorda ao toque).
- Toque nele: carinho. Arraste: muda de lugar.
- Se o sistema encerrar o pet, tire o app da otimização de bateria nas configurações do Android.

## Ajustes (em app/src/main/java/com/pet/robo/PetView.kt)
TAMANHO, DORMIR_APOS, SONO_DURACAO e a velocidade (130f) no passeio.
