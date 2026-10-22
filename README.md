# Radar de Compras Públicas

Aplicação Java/Spring Boot que lê os três arquivos em `contratacoes/`, analisa a amostra e popula o banco relacional PostgreSQL. A camada de leitura aceita arquivos JSON envelopados mesmo quando têm extensão `.csv`, além do CSV achatado.

## Executar

Requer Java 21 e um banco PostgreSQL `radar_compras` disponível em `localhost:5883`. A conexão pode ser alterada por `DB_URL`, `DB_USER` e `DB_PASSWORD`. Na raiz do projeto:

```powershell
.\mvnw.cmd spring-boot:run
```

Abra <http://localhost:8080>. Na inicialização, `schema.sql` cria as tabelas se ainda não existirem e a carga atualiza os dados normalizados no PostgreSQL. A API `/api/contratacoes` entrega os registros para o painel e `/api/relacional` resume a carga.

## Esquema original

Os três arquivos representam registros de contratação do PNCP, mas chegam em duas estruturas:

- Modalidades seis e sete: JSON com `resultado` (lista de contratações) e metadados de paginação (`totalRegistros`, `totalPaginas`, `paginasRestantes`). Cada resultado contém campos planos e grupos de órgão/unidade, localização, modalidade, disputa, orçamento sigiloso, situação, amparo legal, valores, datas e objeto.
- Modalidade três: CSV achatado, com prefixo `resultado__` nos campos do registro e metadados de paginação no fim. O leitor tira esse prefixo para alinhar as três fontes.

`numeroControlePNCP` é a chave de negócio da contratação. `orgaoEntidadeCnpj`, códigos da unidade, `unidadeOrgaoCodigoIbge` e códigos das classificações identificam entidades e domínios. O original é preservado em memória para auditoria e a versão normalizada mantém o arquivo de origem.

## Tratamento e padronização

- Strings vazias e o texto literal `null` são interpretados como ausência; no banco, ficam como SQL `NULL`.
- Valores monetários são convertidos para `DECIMAL`; datas para `TIMESTAMP`; ano para inteiro.
- UF é normalizada para maiúsculas e confrontada com a lista de 27 UFs. Valores não reconhecidos são tratados como ausentes e geram ocorrência.
- Rótulos repetidos são guardados em tabelas de domínio por código, preservando o texto de origem.
- Nulos opcionais são contabilizados à parte de problemas. `dataAberturaPropostaPncp`, `orgaoSubrogadoCnpj` e campos de unidade sub-rogada podem não se aplicar a certas compras.
- Regras de qualidade: identificador PNCP ausente; UF ausente/inválida; código/descrição de sigilo ausente ou código fora de 1/2; datas em ordem impossível; valor estimado zero como alerta para revisão manual. Valor zero não é declarado inválido automaticamente.
- `problema_dado` armazena cada alerta com sua contratação e regra. A tabela permite várias ocorrências por registro.

## Esquema relacional

O DDL está em `src/main/resources/schema.sql`:

- `contratacao` guarda número PNCP, ano, objeto, datas, valores e referências separadas para órgão/unidade principal e sub-rogados, além dos domínios.
- `orgao` separa CNPJ, razão social, esfera e poder.
- `unidade` guarda unidade compradora; `municipio` guarda código IBGE e nome; `estado` mantém UF e nome. O vínculo unidade → município → estado desmembra o endereço e evita repetir município/UF em cada contratação. A fonte só traz município/UF, não rua e número.
- `modalidade`, `situacao_compra`, `modo_disputa`, `sigilo` e `amparo_legal` são dimensões de domínio.
- `conceito_termo` e `termo_alias` mapeiam formas equivalentes sem apagar a expressão de origem.

Essa decomposição reduz repetição de rótulos e endereços e facilita análises por estado, município, órgão, modalidade e situação.

## Sinônimos com completude diferente

Na amostra, `orcamentoSigilosoCodigo = 1` aparece junto de `orcamentoSigilosoDescricao = "Compra sem sigilo"`. Ambos descrevem o conceito `ORCAMENTO_SEM_SIGILO`, mas têm completude distinta: o código é compacto e codificado; a descrição é legível e rotulada. O esquema armazena os dois aliases, campo de origem, nível de completude e evidência. É uma equivalência semântica de representação, não uma afirmação de que quaisquer palavras parecidas sejam sinônimas. Novos mapeamentos devem ser revisados com o dicionário oficial do PNCP.

## Limite da análise atual

Há 10 linhas por arquivo (30 no total). Os JSON das modalidades seis e sete informam 85.041 e 51.114 registros na consulta, mas esses registros não estão completos nos arquivos locais. As métricas e a carga relacional descrevem somente as linhas disponíveis; não representam o universo PNCP.

O painel apresenta compras com/sem sigilo, licitações por UF, alertas por UF, nulos em campos relevantes, exemplos de registros, esquema de origem e contagens da carga relacional. Com o conjunto atual, um valor estimado zero deve ser revisado contra a fonte; nulos de sub-rogação e abertura podem ser opcionais. A ausência desses dados não deve ser apresentada como irregularidade.
