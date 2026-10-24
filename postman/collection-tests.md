# EWM — сниппеты Postman-тестов

Готовые блоки для вкладки **Tests**. Скопировал → вставил → поправил под эндпоинт.

---

## 0. Главное правило: 200 ≠ 2xx

`pm.response.to.be.ok` — это **строго 200**. В EWM много успешных ответов с другим кодом:

| Что делаем | Код |
|---|---|
| `POST` (создание) | **201** |
| `DELETE` | **204** |
| `PATCH`, `GET` | 200 |
| ошибки валидации | 400 |
| не найдено | 404 |
| конфликт состояния (повторная заявка, публикация не из `PENDING`) | 409 |

Поэтому схема такая:

- **на уровне коллекции** — мягкие проверки через `to.be` (2xx + тело + JSON);
- **в конкретном запросе** — точный код и структура через `pm.expect`.

И ещё: одно упавшее утверждение внутри `pm.test` прерывает весь этот тест. Поэтому
код статуса и тело проверяем **разными** `pm.test`-блоками — иначе при ошибке кода
мы не увидим, что не так с телом.

---

## 1. Уровень коллекции (общие проверки)

Клик по коллекции → вкладка **Tests** → вставить:

```js
// Общие проверки для всех запросов коллекции.
// Намеренно мягкие: 201 и 204 тоже проходят.
pm.test("Ответ успешный (2xx)", function () {
    pm.response.to.be.success;
});

pm.test("Код ответа не 5xx", function () {
    pm.expect(pm.response.code).to.be.below(500);
});

pm.test("Время ответа меньше 3 с", function () {
    pm.expect(pm.response.responseTime).to.be.below(3000);
});
```

Почему здесь нет `withBody` и `json`: у `DELETE` (204) тела нет, и общий тест падал бы
на каждом удалении. Эти проверки — в конкретных запросах (шаблоны ниже).

> Если коллекция уже импортирована из спеки и в ней 28 запросов — этот блок ставится
> один раз и работает для всех сразу.

---

## 2. Шаблон: создание, 201 + id в теле

Для `registerUser`, `addCategory`, `addEvent`, `saveCompilation`, `addParticipationRequest`, `hit`.

```js
pm.test("Код ответа 201", function () {
    pm.expect(pm.response.code).to.eql(201);
});

pm.test("Ответ содержит тело в формате JSON", function () {
    pm.response.to.be.withBody;
    pm.response.to.be.json;
});

pm.test("В ответе есть id", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property('id');
    pm.expect(body.id).to.be.a('number');
});

// сохраняем id для следующих запросов коллекции
pm.collectionVariables.set('userId', pm.response.json().id);
```

Меняем только последнюю строку (`userId` → `catId`, `eventId`, `compId`, `requestId`).

---

## 3. Шаблон: получение, 200 + массив

Для `getUsers`, `getCategories`, `getCompilations`, `getEvents`, `getStats` и т.п.

```js
pm.test("Код ответа 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});

pm.test("Тело ответа — JSON-массив", function () {
    pm.response.to.be.withBody;
    pm.response.to.be.json;

    const body = pm.response.json();
    pm.expect(body).to.be.an('array');
});

pm.test("Элементы массива содержат ожидаемые поля", function () {
    const body = pm.response.json();
    if (body.length > 0) {
        pm.expect(body[0]).to.have.property('id');
        pm.expect(body[0]).to.have.property('name');
    }
});
```

Для объекта вместо массива:

```js
pm.test("Код ответа 200 и объект нужной формы", function () {
    pm.expect(pm.response.code).to.eql(200);

    const body = pm.response.json();
    pm.expect(body).to.be.an('object');
    pm.expect(body).to.include.keys('id', 'name');
});
```

---

## 4. Шаблон: удаление, 204 без тела

Для `deleteCategory`, `deleteCompilation`, `delete` (пользователь).

```js
pm.test("Код ответа 204", function () {
    pm.expect(pm.response.code).to.eql(204);
});

pm.test("Тело ответа пустое", function () {
    pm.expect(pm.response.text()).to.eql('');
});
```

`withBody` / `json` здесь **не использовать** — упадёт всегда.

---

## 5. Шаблон: изменение, 200

Для `updateCategory`, `updateCompilation`, `updateEvent`, `changeRequestStatus`, `cancelRequest`.

```js
pm.test("Код ответа 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});

pm.test("Изменение применилось", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property('id');
    // конкретное поле проверяем по смыслу запроса, например:
    // pm.expect(body.name).to.eql('Новое имя');
});
```

---

## 6. Негативные кейсы

### 400 — ошибка валидации

```js
pm.test("Код ответа 400", function () {
    pm.expect(pm.response.code).to.eql(400);
});

pm.test("В ответе есть описание ошибки", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property('status', 'BAD_REQUEST');
    pm.expect(body).to.have.property('message');
});
```

### 404 — объект не найден

```js
pm.test("Код ответа 404", function () {
    pm.expect(pm.response.code).to.eql(404);
});

pm.test("Тело содержит статус NOT_FOUND", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property('status', 'NOT_FOUND');
});
```

### 409 — конфликт состояния

```js
pm.test("Код ответа 409", function () {
    pm.expect(pm.response.code).to.eql(409);
});

pm.test("Тело содержит статус CONFLICT", function () {
    const body = pm.response.json();
    pm.expect(body).to.have.property('status', 'CONFLICT');
});
```

> Точные значения `status` (`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`) — из `ErrorResponse`
> в спецификации. Если команда реализовала иначе — правим под факт, но проверку
> кода ответа оставляем.

---

## 7. Pre-request: актуальная дата для `POST /hit`

Формат `timestamp` в stats-сервисе — `yyyy-MM-dd HH:mm:ss`. Вкладка **Pre-request Script**
у запроса `hit`:

```js
const now = new Date();
const pad = (n) => String(n).padStart(2, '0');

const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} `
         + `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;

pm.collectionVariables.set('hitTimestamp', ts);
```

В теле запроса: `"timestamp": "{{hitTimestamp}}"`.

---

## 8. Проверка вложенных данных

Для `getEvent_1` / `getEvent`: счётчики и вложенные объекты.

```js
pm.test("Событие содержит обязательные поля", function () {
    const body = pm.response.json();
    pm.expect(body).to.include.keys('id', 'title', 'annotation', 'state', 'eventDate');
    pm.expect(body.category).to.be.an('object');
    pm.expect(body.initiator).to.be.an('object');
    pm.expect(body.confirmedRequests).to.be.a('number');
    pm.expect(body.views).to.be.a('number');
});
```

Для `getEventParticipants` (массив заявок):

```js
pm.test("Заявки имеют статус CONFIRMED или REJECTED", function () {
    const body = pm.response.json();
    pm.expect(body).to.be.an('array');
    body.forEach((r) => {
        pm.expect(r.status).to.be.oneOf(['CONFIRMED', 'REJECTED']);
    });
});
```

---

## 9. Чек-лист перед экспортом коллекции

- [ ] `baseUrl` и `statsUrl` вынесены в окружение, в запросах — `{{baseUrl}}`.
- [ ] Ни одного захардкоженного id (только `{{userId}}`, `{{eventId}}` и т.д.).
- [ ] Порядок запросов сверху вниз соответствует зависимостям данных.
- [ ] Имена запросов начинаются с `operationId` (сверка с `coverage.md`).
- [ ] На `204` нет проверок тела.
- [ ] Негативные кейсы — в отдельной папке, идут после позитивных.
- [ ] Коллекция экспортирована в `postman/` и закоммичена вместе с окружением.
