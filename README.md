# community-service

Perguntas, conversas, avaliações, vídeos e mediações. O serviço é independente, usa Java 25 e Spring Boot 4.1.1 e recebe requisições externas por meio do api-gateway.

## Stack

- Spring MVC, Spring Data JPA e PostgreSQL
- Flyway para migrações do banco próprio
- Spring Security como resource server JWT (RS256, chave pública de 3072 bits ou mais)
- springdoc-openapi: Swagger UI em `/swagger-ui.html` e contrato em `/v3/api-docs`
- Spring Boot Actuator

## Arquitetura

Cada funcionalidade é um módulo com arquitetura hexagonal, no mesmo padrão do catalog e do profile:

```
br.com.balaio.community
├── shared                  código comum a todos os módulos
│   ├── api                 envelope { dados, erro } e tratamento global de erros
│   ├── exception           erros de negócio (400, 401, 403, 404, 409, 503)
│   ├── security            JWT, papéis e UsuarioAutenticado injetado nos controllers
│   └── config              OpenAPI, relógio e MVC
└── mediacao                um módulo por funcionalidade
    ├── domain              regras puras, sem Spring
    ├── application         casos de uso e portas de saída (port/out)
    ├── persistence         adaptador JPA das portas
    ├── integration         adaptadores HTTP para outros serviços
    ├── scheduling          tarefas agendadas
    └── api                 controllers e DTOs
```

Regras que valem para todos os módulos:

- O domínio não conhece Spring, JPA nem HTTP; a aplicação fala com o mundo só pelas portas.
- Nenhum acesso ao banco de outro serviço: dados de pedidos, peças ou perfis vêm por API.
- Todas as respostas usam o envelope da Fake API do frontend: `{ "dados": ..., "erro": null }` ou `{ "dados": null, "erro": { "codigo", "mensagem" } }`.
- A identidade sempre vem do JWT (`sub` e claim `papel`), nunca de parâmetros da requisição.

## Execução

Porta HTTP padrão: 8084. Para executar pela IDE, defina `COMMUNITY_SERVICE_PORT` (opcional), `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_PUBLIC_KEY_LOCATION`, `JWT_ISSUER`, `JWT_AUDIENCE`, `COMMERCE_SERVICE_URL`, `CATALOG_SERVICE_URL`, `PROFILE_SERVICE_URL` e `BALAIO_INTERNAL_TOKEN`. O banco e suas credenciais são configurados no repositório `platform`.

No Windows: `.\mvnw.cmd spring-boot:run`.

O `Dockerfile` constrói uma imagem própria. Em `platform`, use `docker compose up --build -d community-service` para iniciar este serviço e seu banco ou `docker compose --profile extended up --build -d` para iniciar todos. Health: `http://localhost:8084/actuator/health`.

## Testes

- `.\mvnw.cmd test`: domínio, casos de uso com portas em memória e controllers com segurança (MockMvc).
- Integração com PostgreSQL 18 via Testcontainers (exige Docker): `$env:COMMUNITY_POSTGRES_TEST='true'; .\mvnw.cmd test`.

## Perguntas públicas (PI-185)

- `GET /api/v1/pecas/{slug}/perguntas`: público; lista em ordem de criação.
- `POST /api/v1/pecas/{slug}/perguntas` com `{ "pergunta" }`: qualquer usuário autenticado. O nome exibido vem do profile (`GET /usuario`) e fica gravado com a pergunta.
- `PATCH /api/v1/pecas/{slug}/perguntas/{id}` com `{ "resposta" }`: só o artesão dono da peça.

A existência e o dono da peça vêm do catalog (`GET /pecas/{slug}`, campo `artesao` = `artesao-<id da conta>`), repassando o token de quem chama para que o dono enxergue a própria peça mesmo antes de publicada. Peça inexistente → 404 `PECA_NAO_ENCONTRADA`; pergunta de outra peça → 404 `PERGUNTA_NAO_ENCONTRADA`; quem não é o dono → 403 `SEM_PERMISSAO`; catalog ou profile fora do ar → 503.

## Mediação de pedidos (PI-195)

Comprador autenticado cria `POST /api/v1/admin/mediacoes` com `pedido` (UUID), `assunto` e `relato`; o serviço consulta o commerce com o mesmo Bearer para validar a titularidade (404 se o pedido não existe, 403 se não é do comprador, 503 se o commerce não responde). Comprador lista somente as próprias mediações em `GET /api/v1/admin/mediacoes`; administrador lista todas, coloca em análise por `PATCH /api/v1/admin/mediacoes/{id}` com `estado: "emAnalise"` e decide por `POST /api/v1/admin/mediacoes/{id}/decisao` com `decisao: "cancelar_reembolsar" | "negar" | "manter_conversa"`.

A decisão e seu UUID são persistidos antes do comando interno ao commerce. Em falha de rede, a resposta é 202 e a mediação fica `decisao_pendente`; o agendador tenta de novo com o mesmo identificador, com espera exponencial de até 60 s e no máximo 10 tentativas. Repetir a mesma decisão é idempotente; uma decisão diferente da registrada retorna 409. O reembolso é apenas um resultado demonstrativo do pedido, sem movimentação financeira até existir provedor sandbox.
