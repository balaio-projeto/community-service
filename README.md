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
- Adaptador S3 contra um SeaweedFS via Testcontainers (exige Docker): `$env:COMMUNITY_S3_TEST='true'; .\mvnw.cmd test`.

## Perguntas públicas (PI-185)

- `GET /api/v1/pecas/{slug}/perguntas`: público; lista em ordem de criação.
- `POST /api/v1/pecas/{slug}/perguntas` com `{ "pergunta" }`: qualquer usuário autenticado. O nome exibido vem do profile (`GET /usuario`) e fica gravado com a pergunta.
- `PATCH /api/v1/pecas/{slug}/perguntas/{id}` com `{ "resposta" }`: só o artesão dono da peça.

A existência e o dono da peça vêm do catalog (`GET /pecas/{slug}`, campo `artesao` = `artesao-<id da conta>`), repassando o token de quem chama para que o dono enxergue a própria peça mesmo antes de publicada. Peça inexistente → 404 `PECA_NAO_ENCONTRADA`; pergunta de outra peça → 404 `PERGUNTA_NAO_ENCONTRADA`; quem não é o dono → 403 `SEM_PERMISSAO`; catalog ou profile fora do ar → 503.

## Coleções do usuário (PI-186 e PI-211)

`GET` e `PUT /api/v1/estado/{colecao}` para `favoritos`, `historicoBusca`, `videosCurtidos` e `videosSalvos`, sempre do usuário do token. O `PUT` recebe o mesmo corpo da Fake API (`{ "favoritos": [...] }`) e devolve a lista gravada.

- Itens repetidos e em branco são removidos mantendo a ordem, então a mesclagem local/remota no login pode mandar a união sem gerar duplicatas.
- Favoritos guardam slugs de peça (até 200) e vídeos guardam IDs (até 500); acima do limite → 400. O histórico guarda as 20 buscas mais recentes (início da lista).
- `sacola` continua no commerce; qualquer outra coleção → 404.

## Avaliações e reputação (PI-194)

- `POST /api/v1/pedidos/{id}/avaliacao` com `{ nota, comentario, aspectos[] }`: só o comprador, só pedido `entregue`, uma vez por pedido (409 `PEDIDO_NAO_AVALIAVEL` caso contrário).
- `GET /api/v1/pedidos/{id}/avaliacao`: o comprador consulta a avaliação que fez.
- `GET /api/v1/artesaos/{slug}/reputacao`: público; média com uma casa, total e distribuição por nota, calculados só com as avaliações do community.

Comprador, peça e estado vêm do commerce (`GET /pedidos/{id}` com o token do comprador). O artesão vem do campo `artesaoId` do pedido quando o commerce o envia; sem ele, é lido da peça no catalog (`artesao-<id>`), o que só funciona enquanto a peça está publicada. O profile não é escrito: quem quiser a reputação consulta esta API.

## Conversas por pedido (PI-222)

- `GET` e `POST /api/v1/pedidos/{id}/conversa` com `{ texto }`: só o comprador e o artesão do pedido. Quem não participa recebe 404, sem ver nenhuma mensagem. Ler a conversa marca como lida para quem leu.
- `GET /api/v1/artesao/conversas`: pedidos do artesão do token, com `id`, `pessoa`, `assunto`, `previa`, `quando`, `naoLida` e `retrato`. O parâmetro `?artesao=` da Fake API é ignorado.

Quem participa é confirmado no commerce com o token de quem chama: o comprador via `GET /api/v1/pedidos/{id}` (só responde para o dono) e o artesão via `GET /api/v1/artesao/vendas` (só traz as vendas dele). Se o commerce não responder, o acesso é negado com 503, nunca liberado.

## Vídeos e comentários (PI-200)

- `GET /api/v1/videos`: vídeos publicados; com `?painel=true` e token de artesão, todos os vídeos dele, inclusive rascunhos.
- `GET /api/v1/videos/{id}`: rascunho só aparece para o dono; para os outros, 404.
- `POST /api/v1/videos` com `{ legenda, etiquetas, peca, duracao }` cria um **rascunho**. `PATCH /{id}` altera esses campos e a `situacao`; `DELETE /{id}` remove o vídeo, os arquivos, os comentários e as curtidas/salvos.
- `PUT /{id}/arquivo` (corpo `video/mp4` ou `video/webm`, até 100 MB, `?duracao=m:ss` opcional) e `PUT /{id}/capa` (`image/jpeg|png|webp`, até 5 MB). O arquivo vai para um temporário e só é movido no fim; se algo falhar, nada fica para trás.
- Só é possível publicar depois que o arquivo foi enviado (409 `VIDEO_SEM_ARQUIVO`), então não existe publicação fantasma.
- `GET /{id}/arquivo` (com suporte a `Range`, para o player) e `GET /{id}/capa`.
- `GET/POST /{id}/comentarios` com `{ texto }`: qualquer usuário autenticado comenta; `artesao: true` quando quem comenta é o dono do vídeo. O nome vem do profile.
- `curtidas` é a contagem de usuários com o vídeo em `videosCurtidos`; `visualizacoes` ainda não é medida (0).

### Armazenamento dos arquivos

A porta `ArmazenamentoMidiaPort` tem dois adaptadores, escolhidos por `COMMUNITY_MIDIA_ARMAZENAMENTO`:

| Valor | Uso | Variáveis |
|---|---|---|
| `local` (padrão) | Desenvolvimento e testes | `COMMUNITY_MIDIA_DIR` (padrão `./dados/midia`) |
| `s3` | Produção no Cloudflare R2 (ou outro S3 compatível) | `COMMUNITY_S3_ENDPOINT` (R2: `https://<account-id>.r2.cloudflarestorage.com`), `COMMUNITY_S3_BUCKET`, `COMMUNITY_S3_CHAVE_ACESSO`, `COMMUNITY_S3_CHAVE_SECRETA`, `COMMUNITY_S3_REGIAO` (padrão `auto`) |

- Os vídeos sempre **passam pelo community**: o bucket pode ficar privado, e a regra "rascunho só para o dono" vale também para os arquivos.
- O `Range` é repassado ao storage. Pular para o meio do vídeo baixa só aquele trecho do R2.
- O upload é gravado num temporário, com limite de tamanho, antes de ir ao bucket. Assim um envio cortado nunca vira objeto.
- O contrato da API (`url`, `/arquivo`, `/capa`) é o mesmo nos dois modos.

## Mediação de pedidos (PI-195)

Comprador autenticado cria `POST /api/v1/admin/mediacoes` com `pedido` (UUID), `assunto` e `relato`; o serviço consulta o commerce com o mesmo Bearer para validar a titularidade (404 se o pedido não existe, 403 se não é do comprador, 503 se o commerce não responde). Comprador lista somente as próprias mediações em `GET /api/v1/admin/mediacoes`; administrador lista todas, coloca em análise por `PATCH /api/v1/admin/mediacoes/{id}` com `estado: "emAnalise"` e decide por `POST /api/v1/admin/mediacoes/{id}/decisao` com `decisao: "cancelar_reembolsar" | "negar" | "manter_conversa"`.

A decisão e seu UUID são persistidos antes do comando interno ao commerce. Em falha de rede, a resposta é 202 e a mediação fica `decisao_pendente`; o agendador tenta de novo com o mesmo identificador, com espera exponencial de até 60 s e no máximo 10 tentativas. Repetir a mesma decisão é idempotente; uma decisão diferente da registrada retorna 409. O reembolso é apenas um resultado demonstrativo do pedido, sem movimentação financeira até existir provedor sandbox.
