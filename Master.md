⚙️ Конфигурация /etc/gitlab/gitlab.rb
Ниже — минимальный, стабильный, рабочий конфиг для HTTPS на порту 9000:

ruby
external_url "https://IP:9000"

letsencrypt['enable'] = false

nginx['listen_port'] = 9000 (443)
nginx['listen_https'] = true
nginx['redirect_http_to_https'] = true

nginx['ssl_certificate'] = "/etc/gitlab/ssl/gitlab.crt"
nginx['ssl_certificate_key'] = "/etc/gitlab/ssl/gitlab.key"

nginx['ssl_verify_client'] = "off"
❗ Критические правила
НЕ использовать redirect_http_to_https_port = true

НЕ использовать nginx['listen_port'] = true

НЕ включать ssl_verify_client on (иначе GitLab требует клиентский сертификат)

Порт должен быть числом, не boolean

🔐 Генерация self‑signed сертификата (офлайн)
Создаём директорию:

bash
sudo mkdir -p /etc/gitlab/ssl
sudo chmod 700 /etc/gitlab/ssl
Генерируем сертификат:

bash
sudo openssl req -newkey rsa:4096 -nodes -keyout /etc/gitlab/ssl/gitlab.key -x509 -days 3650 
  -out /etc/gitlab/ssl/gitlab.crt -subj "/CN=62.152.34.65"
Права:

bash
sudo chmod 600 /etc/gitlab/ssl/gitlab.key
sudo chmod 644 /etc/gitlab/ssl/gitlab.crt
🌐 Настройка HTTPS на порту 9000
После изменения gitlab.rb:

bash
sudo gitlab-ctl reconfigure
sudo gitlab-ctl restart
Проверка:

bash
sudo gitlab-ctl status
Ожидаем:

nginx — run

puma — run

sidekiq — run

🪟 Установка сертификата в Windows
Скопировать gitlab.crt на Windows

Открыть certmgr.msc

Перейти:
Trusted Root Certification Authorities → Certificates

ПКМ → Import

Выбрать gitlab.crt

Установить в Trusted Root

Теперь Git, VSCode и браузер доверяют GitLab.

🔗 Настройка Git remote
Удалить старый origin:

bash
git remote rm origin
Добавить новый:

bash
git remote add origin https://IP:9000/wb/wb-app.git
Проверить:

bash
git remote -v
🚀 Первый push/pull
1. Добавить файлы
bash
git add .
git commit -m "init"
2. Подтянуть серверную историю (если есть)
bash
git pull --rebase origin main
3. Установить upstream
bash
git branch --set-upstream-to=origin/main main
4. Пуш
bash
git push
🧪 Диагностика ошибок
❌ SEC_E_UNTRUSTED_ROOT
Windows не доверяет сертификату
→ установить gitlab.crt в Trusted Root

❌ non-fast-forward
Локальная ветка отстаёт
→ git pull --rebase

❌ invalid port in "*:true"
В gitlab.rb ошибка:
→ nginx['listen_port'] = 9000

❌ GitLab не открывается
Проверить nginx:

bash
sudo gitlab-ctl tail nginx
🎯 Итог
Этот README покрывает:

настройку HTTPS
генерацию сертификата
исправление ошибок nginx
установку сертификата в Windows
настройку Git remote
первый push/pull
диагностику

sudo chmod 600 /etc/gitlab/ssl/wb-gitlab.key
sudo chmod 644 /etc/gitlab/ssl/wb-gitlab.crt

копируем сертификат в Ubuntu
sudo cp /etc/gitlab/ssl/wb-gitlab.crt /usr/local/share/ca-certificates/wb-gitlab.crt

ls -l /etc/ssl/certs | grep gitlab
sudo systemctl restart gitlab-runner
sudo gitlab-runner verify

Код
https://IP:9000/<group>/<project>/-/pipelines
🧯 Типовые ошибки и решения
❌ tls: failed to verify certificate: x509: certificate relies on legacy CN
Сертификат без SAN → пересоздать с SAN.

❌ x509: certificate signed by unknown authority
Runner не доверяет сертификату → установить в /usr/local/share/ca-certificates.

❌ incompatible types: TOML value has type map[string]any
config.toml повреждён → заменить на правильный.

❌ Runner в user‑mode
Удалить:

Код
rm -rf ~/.gitlab-runner
🎉 Итог
Этот README полностью покрывает:

установку Runner
доверие сертификату
регистрацию
исправление ошибок
минимальный CI/CD pipeline
проверку работоспособности

==================

📦 Требования
GitLab Runner зарегистрирован и ONLINE

сертификат GitLab установлен в доверенные корни Linux
Runner работает в system‑mode
executor = shell
у Runner есть доступ к /home/gribanov/<project>

Проверка Runner:

Код
sudo gitlab-runner list
Ожидаем:

Код
ai1-shell-runner  (shell)  active
🚀 Минимальный офлайн pipeline
Создай файл:

Код
.gitlab-ci.yml
Содержимое:

yaml
stages:
  - test
  - deploy

test-runner:
  stage: test
  script:
    - echo "Runner работает"
    - hostname
    - date

deploy-local:
  stage: deploy
  script:
    - echo "Деплой в локальную папку"
    - PROJECT_DIR="/home/gribanov/ai-hub"
    - mkdir -p "$PROJECT_DIR"
    - rsync -av --delete ./ "$PROJECT_DIR/"
  only:
    - main
Что делает pipeline
test-runner — проверяет, что Runner жив

deploy-local — копирует весь репозиторий в /home/gribanov/ai-hub

📂 Автодеплой в локальную папку
Папка деплоя:

Код
/home/gribanov/wb-app
Если хочешь другое имя — меняешь:

yaml
PROJECT_DIR="/home/gribanov/<имя>"
Почему rsync?
работает офлайн

быстро

удаляет старые файлы (--delete)

копирует только изменения

🔐 Права и безопасность
Runner запускается от пользователя:

Код
gitlab-runner
Поэтому нужно дать ему доступ к папке деплоя:

Код
sudo mkdir -p /home/gribanov/wb-app
sudo chown -R gitlab-runner:gitlab-runner /home/gribanov/wb-app
Если хочешь, чтобы деплой выполнялся от root:

Код
sudo visudo
Добавить:

Код
gitlab-runner ALL=(ALL) NOPASSWD: ALL
И в .gitlab-ci.yml:

yaml
script:
  - sudo rsync ...
🧪 Проверка pipeline
После пуша:

Код
git add .
git commit -m "ci deploy test"
git push
Открыть:

Код
https://IP:9000/<group>/<project>/-/pipelines
Ожидаем:

test-runner → passed

deploy-local → passed

Проверяем папку:

Код
ls -la /home/gribanov/ai-hub
Должны появиться файлы проекта.

🧯 Диагностика ошибок
❌ Runner не запускает job
Проверить теги:

Код
sudo gitlab-runner list
Если Runner имеет теги:

Код
tags = ["shell"]
То job должен иметь:

yaml
tags:
  - shell
❌ Permission denied при деплое
Дать права:

Код
sudo chown -R gitlab-runner:gitlab-runner /home/gribanov/ai-hub
❌ Сертификат недоверенный
Установить:

Код
sudo cp /etc/gitlab/ssl/gitlab.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates
🎉 Итог
Теперь у тебя:

офлайн GitLab
офлайн Runner
офлайн CI/CD
автодеплой в локальную папку
