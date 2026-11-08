# community-service

Perguntas, conversas, avaliações, vídeos e mediações. O serviço é independente, usa Java 25 e Spring Boot 4.1.1 e recebe requisições externas por meio do api-gateway.

## Stack inicial

- Spring MVC, Spring Data JPA e PostgreSQL
- Flyway para migrações do banco próprio
- Bean Validation
- Spring Boot Actuator

## Execução

Porta HTTP padrão: 8084. Para executar pela IDE, defina `COMMUNITY_SERVICE_PORT` (opcional), `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. O banco e suas credenciais são configurados no repositório `platform`.

No Windows: `.\mvnw.cmd spring-boot:run`. Testes: `.\mvnw.cmd test`.

O `Dockerfile` constrói uma imagem própria. Em `platform`, use `docker compose up --build -d community-service` para iniciar este serviço e seu banco ou `docker compose --profile extended up --build -d` para iniciar todos. Health: `http://localhost:8084/actuator/health`.

## Mediação de pedidos (PI-190)

Comprador autenticado cria `POST /api/v1/admin/mediacoes` com `pedido` (UUID), `assunto` e `relato`; o serviço consulta o commerce com o mesmo Bearer para validar a titularidade. Comprador lista somente as próprias mediações em `GET /api/v1/admin/mediacoes`; administrador lista todas, abre por `PATCH /api/v1/admin/mediacoes/{id}` com `estado: "emAnalise"` e decide por `POST /api/v1/admin/mediacoes/{id}/decisao` com `decisao: "cancelar_reembolsar" | "negar" | "manter_conversa"`.

A decisão e seu UUID são persistidos antes do comando interno ao commerce. Em falha de rede, fica `decisao_pendente`; o worker tenta novamente com o mesmo identificador. O reembolso é apenas um resultado demonstrativo do pedido, sem movimentação financeira até existir provedor sandbox. Para testar com PostgreSQL 18 via Testcontainers: `$env:COMMUNITY_POSTGRES_TEST='true'; .\mvnw.cmd '-Djava.version=21' clean verify`.
