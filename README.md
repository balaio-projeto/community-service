# Community Service

Serviço de interações e conteúdo comunitário do Balaio: perguntas sobre peças, conversas, avaliações, mediações e vídeos dos ateliês.

## Estado atual

Há somente o esqueleto Spring Boot e dependências para persistência, validação e monitoramento. Não existem endpoints, entidades ou migrações de negócio implementados. As rotas planejadas vêm da [documentação do frontend](https://github.com/lucaschavessf2/Balaio/blob/main/Frontend/docs/api.md).

## Responsabilidades e limites

- Manter perguntas e respostas relacionadas às peças.
- Manter conversas entre comprador e artesão vinculadas a pedidos.
- Registrar avaliações após a entrega.
- Abrir e analisar mediações.
- Manter metadados de vídeos e comentários. Armazenamento e reprodução dos arquivos de vídeo ainda exigem decisão; a Fake API trabalha principalmente com metadados.
- Validar identidade, papel, participação no pedido e regras de moderação no backend real.

O `catalog-service` é dono das peças e o `commerce-service` é dono dos pedidos. Não consultar diretamente os respectivos bancos.

## API planejada

Prefixo `/api/v1`; nenhuma rota está implementada ainda.

| Método e rota | Uso |
| --- | --- |
| `GET/POST /pecas/{slug}/perguntas` | Consultar/publicar pergunta |
| `GET/POST /pedidos/{id}/conversa` | Consultar/enviar mensagens |
| `POST /pedidos/{id}/avaliacao` | Avaliar pedido entregue |
| `GET /admin/mediacoes` | Consultar mediações |
| `POST /admin/mediacoes` | Abrir mediação de pedido |
| `PATCH /admin/mediacoes/{id}` | Atualizar análise |
| `GET/POST /videos`, `GET /videos/{id}` | Consultar feed e metadados de vídeos |
| `GET/POST /videos/{id}/comentarios` | Consultar/publicar comentários |

As rotas e permissões devem ser validadas com as histórias antes de criar controllers. A Fake API não define a política real de autorização nem o armazenamento binário dos vídeos.

## Contrato com o frontend

Usar a Fake API como referência de payloads e estados. Manter `/api/v1` e o envelope `{ "dados": ..., "erro": null }`; documentar códigos HTTP para validação, autenticação, permissão e conflitos.

## Stack e desenvolvimento

- Java 25, Maven Wrapper, Spring Boot 4.1.1.
- Spring MVC, Spring Data JPA, Bean Validation, PostgreSQL, Flyway e Actuator.
- Requer JDK 25 e PostgreSQL. Quando disponível, use a infraestrutura do repositório `platform`; configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` com os valores da base própria do serviço. Não versione credenciais.
- Migrações em `src/main/resources/db/migration`, por exemplo `V1__create_community_content.sql`.
- O `application.yaml` atual só define o nome da aplicação.

Windows: ` .\mvnw.cmd spring-boot:run`, ` .\mvnw.cmd test`, ` .\mvnw.cmd package`. Em Linux/macOS, use `./mvnw`.

Com Actuator, consulte `/actuator/health`. Testes de autorização, titularidade e regras de avaliação/mediação devem acompanhar os endpoints.

## OpenAPI / Swagger UI

Ainda não está no `pom.xml`. Recomendação: adicionar OpenAPI e Swagger UI junto aos primeiros controllers com `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.x para Spring Boot 4 (versão estável consultada: 3.1.1). URLs esperadas após configurar: `/swagger-ui/index.html` e `/v3/api-docs`.

## Referências

- [Contrato da API do frontend](https://github.com/lucaschavessf2/Balaio/blob/main/Frontend/docs/api.md)
- [Fake API](https://github.com/lucaschavessf2/Balaio/tree/main/Frontend/fake-api)
- [springdoc-openapi](https://springdoc.org/)
