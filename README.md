# MBA Hexagonal Architecture — Cancelamento de Evento

Plataforma de ingressos em Arquitetura Hexagonal e Clean Architecture (Java 17, Gradle, Spring Boot), dividida nos módulos `domain`, `application` e `infrastructure`. Este fork adiciona a feature de cancelamento de evento, com a cascata assíncrona que cancela os ingressos do evento.

## Pré-requisitos

- JDK 17 (o Gradle Wrapper 8.2.1 do projeto não roda em JDKs mais novos que 20)
- Docker e Docker Compose (para o MySQL da aplicação)

## Subindo o projeto

```bash
# MySQL 8 na porta 3306, banco "events" (usuário root / senha root)
docker compose up -d

# Aplicação na porta 8080
./gradlew :infrastructure:bootRun
```

O GraphiQL fica disponível em `http://localhost:8080/graphiql`.

## Rodando os testes

```bash
./gradlew test
```

Os testes de integração da camada `infrastructure` usam o perfil `test`, com banco H2 em memória, então não dependem do MySQL. Sem um JDK 17 local, dá para rodar a suíte num container:

```bash
docker run --rm -v "$PWD":/app -w /app eclipse-temurin:17-jdk ./gradlew test
```

## A feature

| Operação | REST | GraphQL |
|---|---|---|
| Cancelar evento | `POST /events/{id}/cancel` → `200 {id, status}` ou `422` com a mensagem | `mutation { cancelEvent(id) { id status } }` |
| Consultar evento | `GET /events/{id}` → completo `{id, name, date, totalSpots, status}`; com header `X-Public` → reduzido `{id, status}`; `404` se não existir | `query { eventOfId(id) { id name date totalSpots status } }` |

### Onde a cascata de cancelamento acontece

O `CancelEventUseCase` apenas chama `Event.cancel()`, que muda o estado do agregado para `CANCELLED` e registra o evento de domínio `EventCancelled` (type `event.cancelled`). Ao persistir o evento, o `EventDatabaseRepository` grava os eventos de domínio do agregado na tabela `outbox` na mesma transação. O `OutboxRelay` publica as mensagens pendentes no `QueueGateway`, e o `ConsumerQueueGateway` roteia o tipo `event.cancelled` para o `CancelEventTicketsUseCase`, que busca os ingressos do evento por `TicketRepository.ticketsByEventId` e chama `Ticket.cancel()` em cada um (de forma idempotente). Assim, o agregado `Event` nunca toca o agregado `Ticket`, e o comando de cancelamento não cancela ingressos de forma síncrona: é o mesmo caminho já usado por `EventTicketReserved` → `CreateTicketForCustomerUseCase`.

Os testes que cobrem esse caminho são o `CancelEventUseCaseIT` (o `EventCancelled` chega à outbox) e o `ConsumerQueueGatewayIT` (o JSON do `EventCancelled` publicado no `ConsumerQueueGateway` termina com todos os ingressos do evento em `CANCELLED`, inclusive no reprocessamento).
