#!/bin/bash
set -e

PROJECT_DIR="/home/gribanov/apps"

# Auto-detect docker compose
if command -v docker-compose &> /dev/null; then
  COMPOSE="docker-compose"
else
  COMPOSE="docker compose"
fi

FRONTEND_SERVICE="frontend"
FRONTEND_CONTAINER="wb-frontend"
HEALTHCHECK_URL="http://localhost:3000"

log() {
  echo -e "\033[1;32m[$1]\033[0m $2"
}

case "$1" in

  build)
    log "BUILD" "Пересборка образов"
    $COMPOSE build --no-cache
    ;;

  up)
    log "UP" "Запуск сервисов"
    $COMPOSE up -d
    ;;

  down)
    log "DOWN" "Остановка сервисов"
    $COMPOSE down
    ;;

  restart)
    log "RESTART" "Перезапуск сервисов"
    $COMPOSE down
    $COMPOSE up -d
    ;;

  logs)
    log "LOGS" "Логи сервисов"
    $COMPOSE logs -f
    ;;

  pull)
    log "PULL" "Обновление проекта из GitLab"
    git pull --rebase
    ;;

  deploy)
    log "DEPLOY" "Деплой в $PROJECT_DIR"
    mkdir -p "$PROJECT_DIR"
    rsync -av --delete \
      --exclude ".git" \
      --exclude "node_modules" \
      --exclude ".nuxt" \
      --exclude ".output" \
      ./ "$PROJECT_DIR/"
    ;;

  deploy-frontend)
    log "DEPLOY FRONTEND" "Сохранение предыдущего образа"
    docker tag $FRONTEND_CONTAINER:latest $FRONTEND_CONTAINER:previous || true

    log "DEPLOY FRONTEND" "Сборка нового образа"
    $COMPOSE build $FRONTEND_SERVICE

    log "DEPLOY FRONTEND" "Запуск нового контейнера"
    $COMPOSE up -d $FRONTEND_SERVICE

    log "HEALTHCHECK" "Проверка доступности $HEALTHCHECK_URL"
    sleep 5

    if curl -f "$HEALTHCHECK_URL" > /dev/null 2>&1; then
      log "HEALTHCHECK" "OK — сервис работает"
    else
      log "ERROR" "Healthcheck провалился — выполняем rollback"
      ./run.sh rollback
      exit 1
    fi
    ;;

  rollback)
    log "ROLLBACK" "Откат на предыдущую версию"

    docker stop $FRONTEND_CONTAINER || true
    docker rm $FRONTEND_CONTAINER || true

    log "ROLLBACK" "Запуск предыдущего образа"
    docker run -d \
      --name $FRONTEND_CONTAINER \
      -p 3000:3000 \
      $FRONTEND_CONTAINER:previous

    log "ROLLBACK" "Готово"
    ;;

  status)
    log "STATUS" "Состояние контейнеров"
    $COMPOSE ps
    ;;

  clean)
    log "CLEAN" "Удаление dangling-образов"
    docker image prune -f
    ;;

  *)
    echo "Использование: ./run.sh {build|up|down|restart|logs|pull|deploy|deploy-frontend|rollback|status|clean}"
    exit 1
    ;;
esac
