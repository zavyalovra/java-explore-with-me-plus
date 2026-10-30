# EWM — чек-лист покрытия Postman-тестами

Источники: `ewm-stats-service-spec.json` и `ewm-main-service-spec.json` в корне репозитория (`info.version` = 1.0).
Всего **28** эндпоинтов: 2 в stats-сервисе + 26 в main-сервисе.

Обозначения в колонке «Статус»:

- `☐` — запроса в коллекции нет;
- `✎` — запрос добавлен, тест не написан;
- `✔` — запрос добавлен и тест проходит;
- `✖` — запрос есть, но падает (баг API или неверные данные).

Правило именования запросов в Postman: `<operationId> — <краткое описание>`, например
`registerUser — создать пользователя`. Тогда имя в коллекции однозначно сопоставляется
со строкой этой таблицы.

---

## Этап 1. Сервис статистики (`statsUrl`, 9090)

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить в переменные |
|---|---|---|---|---|---|
| ☐ | `POST` | `/hit` | `hit` | 201 | — |
| ☐ | `GET` | `/stats` | `getStats` | 200 | — |

Особенности:

- `POST /hit` — тело `EndpointHitDto` (`app`, `uri`, `ip`, `timestamp` в формате `yyyy-MM-dd HH:mm:ss`).
- `GET /stats` — обязательны `start` и `end` (URL-encoded), `uris` — опционально (массив, в query повторяется).
- В ответе `getStats` — массив `ViewStatsDto` (`app`, `uri`, `hits`).

---

## Этап 2. Основной сервис (`baseUrl`, 8080)

### Public: Категории

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/categories` | `getCategories` | 200 | — |
| ☐ | `GET` | `/categories/{catId}` | `getCategory` | 200 | — |

### Public: Подборки событий

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/compilations` | `getCompilations` | 200 | — |
| ☐ | `GET` | `/compilations/{compId}` | `getCompilation` | 200 | — |

### Public: События

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/events` | `getEvents_1` | 200 | — |
| ☐ | `GET` | `/events/{id}` | `getEvent_1` | 200 | — |

Особенности: отдаются только **опубликованные** события; `GET /events` уходит в stats;
`GET /events/{id}` для черновика — 404.

### Private: Запросы на участие

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/users/{userId}/requests` | `getUserRequests` | 200 | — |
| ☐ | `POST` | `/users/{userId}/requests` | `addParticipationRequest` | 201 | `requestId` |
| ☐ | `PATCH` | `/users/{userId}/requests/{requestId}/cancel` | `cancelRequest` | 200 | — |

Особенности: заявку нельзя подать на своё событие (409/400), на неопубликованное — 409,
повторно — 409. Дополнительно: `?eventId=` у `POST .../requests`.

### Private: События

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/users/{userId}/events` | `getEvents` | 200 | — |
| ☐ | `POST` | `/users/{userId}/events` | `addEvent` | 201 | `eventId` |
| ☐ | `GET` | `/users/{userId}/events/{eventId}` | `getEvent` | 200 | — |
| ☐ | `PATCH` | `/users/{userId}/events/{eventId}` | `updateEvent` | 200 | — |
| ☐ | `GET` | `/users/{userId}/events/{eventId}/requests` | `getEventParticipants` | 200 | — |
| ☐ | `PATCH` | `/users/{userId}/events/{eventId}/requests` | `changeRequestStatus` | 200 | — |

Особенности: `PATCH .../{eventId}` — `UpdateEventUserRequest` с `stateAction`
(`SEND_TO_REVIEW`, `CANCEL_REVIEW`); `PATCH .../requests` — `EventRequestStatusUpdateRequest`
с `stateAction` (`CONFIRM`, `REJECT`) и `requestIds`.

### Admin: Категории

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `POST` | `/admin/categories` | `addCategory` | 201 | `catId` |
| ☐ | `DELETE` | `/admin/categories/{catId}` | `deleteCategory` | 204 | — |
| ☐ | `PATCH` | `/admin/categories/{catId}` | `updateCategory` | 200 | — |

### Admin: Подборки событий

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `POST` | `/admin/compilations` | `saveCompilation` | 201 | `compId` |
| ☐ | `DELETE` | `/admin/compilations/{compId}` | `deleteCompilation` | 204 | — |
| ☐ | `PATCH` | `/admin/compilations/{compId}` | `updateCompilation` | 200 | — |

Особенности: подборка может быть без событий (`events` — пустой массив). В коллекции
`saveCompilation` передаёт `eventId` из папки «События» и проверяет, что событие попало
в подборку, а `getCompilation` — что оно там же и возвращается.

### Admin: Пользователи

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/admin/users` | `getUsers` | 200 | — |
| ☐ | `POST` | `/admin/users` | `registerUser` | 201 | `userId` |
| ☐ | `DELETE` | `/admin/users/{userId}` | `delete` | 204 | — |

### Admin: События

| Статус | Метод | Путь | operationId | Ожидаемый код | Нужно сохранить |
|---|---|---|---|---|---|
| ☐ | `GET` | `/admin/events` | `getEvents_2` | 200 | — |
| ☐ | `PATCH` | `/admin/events/{eventId}` | `updateEvent_1` | 200 | — |

Особенности: `PATCH` — `UpdateEventAdminRequest` с `stateAction`
(`PUBLISH_EVENT`, `REJECT_EVENT`); публиковать можно только из `PENDING`,
отклонять — только неопубликованное.

---

## Порядок выполнения в коллекции (важно!)

Запросы выполняются сверху вниз, поэтому порядок фиксирован:

```
1. registerUser            → userId   (автор события)
2. registerUser (второй)   → userId2  (участник)
3. addCategory             → catId
4. addEvent                → eventId  (status = PENDING)
5. updateEvent             (stateAction = PUBLISH_EVENT от админа
                            или PATCH /admin/events/{eventId})
6. addParticipationRequest → requestId (от userId2)
7. changeRequestStatus     (CONFIRM, requestIds = [requestId])
8. getUserRequests / getEventParticipants
9. cancelRequest
10. проверки Public-группы (getEvents_1, getEvent_1, getCategories, ...)
11. deleteCategory / deleteCompilation / delete
```

Негативные кейсы — отдельная папка `Negative`, запускать после позитивных,
чтобы не ломать подготовленные данные.

---

## Итог

| Группа | Эндпоинтов | Готово |
|---|---|---|
| stats | 2 | 0 |
| Public | 6 | 0 |
| Private | 9 | 0 |
| Admin | 11 | 0 |
| **Всего** | **28** | **0** |
