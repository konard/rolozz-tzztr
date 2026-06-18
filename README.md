# wb-app

Add pipelines ci/cd

## Getting started

🚀 Установка NVM (Node Version Manager)
1) Устанавливаем nvm
bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
Затем активируем:

bash
export NVM_DIR="$HOME/.nvm"
source "$NVM_DIR/nvm.sh"
Чтобы nvm работал всегда — добавляем в ~/.bashrc:

bash
echo 'export NVM_DIR="$HOME/.nvm"' >> ~/.bashrc
echo '[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"' >> ~/.bashrc
Перезагружаем сессию:

bash
source ~/.bashrc
Проверяем:

bash
nvm --version
🚀 2) Устанавливаем Node 26 (как на Windows)
bash
nvm install 26
nvm use 26
nvm alias default 26
Проверяем:

bash
node -v
npm -v
🚀 3) Делаем Node/npm доступными для GitLab Runner (shell‑executor)
GitLab Runner запускает job не под твоим пользователем, а под пользователем:

Код
gitlab-runner
Поэтому нужно включить nvm и для него.

3.1) Копируем nvm в его домашнюю директорию
bash
sudo mkdir -p /home/gitlab-runner/.nvm
sudo cp -r ~/.nvm/* /home/gitlab-runner/.nvm/
sudo chown -R gitlab-runner:gitlab-runner /home/gitlab-runner/.nvm
3.2) Добавляем nvm в его .bashrc
bash
sudo bash -c 'echo "export NVM_DIR=\"/home/gitlab-runner/.nvm\"" >> /home/gitlab-runner/.bashrc'
sudo bash -c 'echo "[ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\"" >> /home/gitlab-runner/.bashrc'
3.3) Устанавливаем Node 26 для gitlab-runner
bash
sudo -u gitlab-runner bash -lc "nvm install 26"
sudo -u gitlab-runner bash -lc "nvm alias default 26"
sudo -u gitlab-runner bash -lc "node -v"
sudo -u gitlab-runner bash -lc "npm -v"
Если вывод есть — всё работает.

🎯 4) Проверяем, что CI теперь видит npm
Запускаем тестовый pipeline:

yaml
test-npm:
  stage: build
  script:
    - node -v
    - npm -v
Если CI выводит версии — победа.

🎉 Итог
Теперь:

GitLab Runner видит npm и node
build-job не падает
CI/CD работает стабильно

gribanov@fxyulowibm:/tmp$ nvm install node lts

🎯 1. Устанавливаем libatomic (обязательно для Node 26)
На Ubuntu/Debian:

sudo apt update
sudo apt install -y libatomic1
ldconfig -p | grep libatomic
Должно появиться:
libatomic.so.1 (libc6,x86-64) => /usr/lib/x86_64-linux-gnu/libatomic.so.1

bash
node -v
npm -v

🎯 Решение: подключить nvm в .bash_profile (а не в .bashrc)
У пользователя gitlab-runner нет .bash_profile, поэтому создаём его.

✔️ 1. Создаём .bash_profile для gitlab-runner
bash
sudo bash -c 'echo "export NVM_DIR=\"/home/gitlab-runner/.nvm\"" > /home/gitlab-runner/.bash_profile'
sudo bash -c 'echo "[ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\"" >> /home/gitlab-runner/.bash_profile'
sudo chown gitlab-runner:gitlab-runner /home/gitlab-runner/.bash_profile
✔️ 2. Проверяем, что nvm теперь подхватывается
bash
sudo -u gitlab-runner bash -lc "nvm --version"
Если видишь версию — победа.

🎯 Как убедиться, что это OOM (Out Of Memory)
На сервере:

Код
dmesg -T | grep -i kill
Ты увидишь:

Код
Out of memory: Kill process 12345 (node) score 987 or sacrifice child
Killed process 12345 (node)
🎯 Почему это происходит именно в CI, а не локально
Потому что:

локально у тебя 16–64 GB RAM

на сервере runner’а — 1–2 GB

Nuxt 4 в CI собирается в продакшн‑режиме, что требует больше памяти

SSR + prerenderer → жрут память

Vite 7 + Rollup → жрут память

🎯 Решение №1 (лучшее): отключить prerender в CI
В nuxt.config.ts:

ts
export default defineNuxtConfig({
  nitro: {
    prerender: {
      crawlLinks: false,
      routes: []
    }
  }
})
Или вообще:

ts
prerender: false
Nuxt 4 в CI не должен делать prerender, это делает Docker‑сборка.

🎯 Решение №2: ограничить память Node в CI
В build‑job:

yaml
script:
  - export NODE_OPTIONS="--max-old-space-size=2048"
  - npm ci
  - npm run build
Это даёт Node 2 GB памяти.

🎯 Решение №3: увеличить swap на сервере (очень эффективно)
Если у тебя 1–2 GB RAM — добавляем swap:

Код
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
Проверяем:

Код
free -h
Теперь у runner’а есть 4 GB виртуальной памяти.

🎯 Решение №4: собирать Nuxt в Docker, а не в CI
Это лучший продакшн‑вариант.

Вместо:

Код
npm ci
npm run build
делаем:

yaml
script:
  - docker compose build frontend
И CI больше не жрёт память — всё делает Docker.

🎯 Решение №5: отключить SSR в CI (временно)
В nuxt.config.ts:

ts
ssr: false
Но это костыль.

🎯 Что тебе делать прямо сейчас (пошагово)
✔️ Шаг 1 — добавить swap (самое быстрое)
Код
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
✔️ Шаг 2 — добавить NODE_OPTIONS в CI
yaml
script:
  - export NODE_OPTIONS="--max-old-space-size=2048"
  - npm ci
  - npm run build
✔️ Шаг 3 — отключить prerender в CI
В nuxt.config.ts:

ts
nitro: {
  prerender: false
}
🎉 Итог
Ошибка:

Код
Killed
— это OOM Killer, а не проблема Nuxt.

После:

swap

NODE_OPTIONS

отключения prerender

CI будет собирать Nuxt 4 стабильно.

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

## Add your files

* [Create](https://docs.gitlab.com/user/project/repository/web_editor/#create-a-file) or [upload](https://docs.gitlab.com/user/project/repository/web_editor/#upload-a-file) files
* [Add files using the command line](https://docs.gitlab.com/topics/git/add_files/#add-files-to-a-git-repository) or push an existing Git repository with the following command:

```
cd existing_repo
git remote add origin http://155.212.188.246/wb-apps/wb-app.git
git branch -M main
git push -uf origin main
```

## Integrate with your tools

* [Set up project integrations](http://155.212.188.246/wb-apps/wb-app/-/settings/integrations)

## Collaborate with your team

* [Invite team members and collaborators](https://docs.gitlab.com/user/project/members/)
* [Create a new merge request](https://docs.gitlab.com/user/project/merge_requests/creating_merge_requests/)
* [Automatically close issues from merge requests](https://docs.gitlab.com/user/project/issues/managing_issues/#closing-issues-automatically)
* [Enable merge request approvals](https://docs.gitlab.com/user/project/merge_requests/approvals/)
* [Set auto-merge](https://docs.gitlab.com/user/project/merge_requests/auto_merge/)

## Test and Deploy

Use the built-in continuous integration in GitLab.

* [Get started with GitLab CI/CD](https://docs.gitlab.com/ci/quick_start/)
* [Analyze your code for known vulnerabilities with Static Application Security Testing (SAST)](https://docs.gitlab.com/user/application_security/sast/)
* [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/topics/autodevops/requirements/)
* [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/user/clusters/agent/)
* [Set up protected environments](https://docs.gitlab.com/ci/environments/protected_environments/)

***

# Editing this README

When you're ready to make this README your own, just edit this file and use the handy template below (or feel free to structure it however you want - this is just a starting point!). Thanks to [makeareadme.com](https://www.makeareadme.com/) for this template.

## Suggestions for a good README

Every project is different, so consider which of these sections apply to yours. The sections used in the template are suggestions for most open source projects. Also keep in mind that while a README can be too long and detailed, too long is better than too short. If you think your README is too long, consider utilizing another form of documentation rather than cutting out information.

## Name
Choose a self-explaining name for your project.

## Description
Let people know what your project can do specifically. Provide context and add a link to any reference visitors might be unfamiliar with. A list of Features or a Background subsection can also be added here. If there are alternatives to your project, this is a good place to list differentiating factors.

## Badges
On some READMEs, you may see small images that convey metadata, such as whether or not all the tests are passing for the project. You can use Shields to add some to your README. Many services also have instructions for adding a badge.

## Visuals
Depending on what you are making, it can be a good idea to include screenshots or even a video (you'll frequently see GIFs rather than actual videos). Tools like ttygif can help, but check out Asciinema for a more sophisticated method.

## Installation
Within a particular ecosystem, there may be a common way of installing things, such as using Yarn, NuGet, or Homebrew. However, consider the possibility that whoever is reading your README is a novice and would like more guidance. Listing specific steps helps remove ambiguity and gets people to using your project as quickly as possible. If it only runs in a specific context like a particular programming language version or operating system or has dependencies that have to be installed manually, also add a Requirements subsection.

## Usage
Use examples liberally, and show the expected output if you can. It's helpful to have inline the smallest example of usage that you can demonstrate, while providing links to more sophisticated examples if they are too long to reasonably include in the README.

## Support
Tell people where they can go to for help. It can be any combination of an issue tracker, a chat room, an email address, etc.

## Roadmap
If you have ideas for releases in the future, it is a good idea to list them in the README.

## Contributing
State if you are open to contributions and what your requirements are for accepting them.

For people who want to make changes to your project, it's helpful to have some documentation on how to get started. Perhaps there is a script that they should run or some environment variables that they need to set. Make these steps explicit. These instructions could also be useful to your future self.

You can also document commands to lint the code or run tests. These steps help to ensure high code quality and reduce the likelihood that the changes inadvertently break something. Having instructions for running tests is especially helpful if it requires external setup, such as starting a Selenium server for testing in a browser.

## Authors and acknowledgment
Show your appreciation to those who have contributed to the project.

## License
For open source projects, say how it is licensed.

## Project status
If you have run out of energy or time for your project, put a note at the top of the README saying that development has slowed down or stopped completely. Someone may choose to fork your project or volunteer to step in as a maintainer or owner, allowing your project to keep going. You can also make an explicit request for maintainers.
