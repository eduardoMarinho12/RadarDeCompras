package bdproject.radardecompras;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RelationalLoader implements CommandLineRunner {
    private static final Map<String,String> STATES = Map.ofEntries(
        Map.entry("AC","Acre"),Map.entry("AL","Alagoas"),Map.entry("AP","Amapá"),Map.entry("AM","Amazonas"),Map.entry("BA","Bahia"),Map.entry("CE","Ceará"),Map.entry("DF","Distrito Federal"),Map.entry("ES","Espírito Santo"),Map.entry("GO","Goiás"),Map.entry("MA","Maranhão"),Map.entry("MT","Mato Grosso"),Map.entry("MS","Mato Grosso do Sul"),Map.entry("MG","Minas Gerais"),Map.entry("PA","Pará"),Map.entry("PB","Paraíba"),Map.entry("PR","Paraná"),Map.entry("PE","Pernambuco"),Map.entry("PI","Piauí"),Map.entry("RJ","Rio de Janeiro"),Map.entry("RN","Rio Grande do Norte"),Map.entry("RS","Rio Grande do Sul"),Map.entry("RO","Rondônia"),Map.entry("RR","Roraima"),Map.entry("SC","Santa Catarina"),Map.entry("SP","São Paulo"),Map.entry("SE","Sergipe"),Map.entry("TO","Tocantins"));
    private final JdbcTemplate jdbc;
    private final AnaliseController source;
    public RelationalLoader(JdbcTemplate jdbc, AnaliseController source) { this.jdbc=jdbc; this.source=source; }

    @Override public void run(String... args) throws Exception {
        List<Map<String,String>> rows=(List<Map<String,String>>)source.contratacoes().get("registros");
        jdbc.update("DELETE FROM problema_dado"); jdbc.update("DELETE FROM contratacao"); jdbc.update("DELETE FROM unidade"); jdbc.update("DELETE FROM municipio"); jdbc.update("DELETE FROM orgao");
        jdbc.update("DELETE FROM modalidade"); jdbc.update("DELETE FROM situacao_compra"); jdbc.update("DELETE FROM modo_disputa"); jdbc.update("DELETE FROM sigilo"); jdbc.update("DELETE FROM amparo_legal");
        STATES.forEach((uf,nome)->jdbc.update("INSERT INTO estado(uf,nome) VALUES (?,?) ON CONFLICT (uf) DO UPDATE SET nome=EXCLUDED.nome",uf,nome));
        for(Map<String,String> r:rows) insert(r);
        seedSynonym();
    }

    private void insert(Map<String,String> r) {
        String uf=v(r,"unidadeOrgaoUfSigla").toUpperCase(); if(!STATES.containsKey(uf)) uf="";
        String cnpj=v(r,"orgaoEntidadeCnpj"), city=v(r,"unidadeOrgaoMunicipioNome"), ibge=v(r,"unidadeOrgaoCodigoIbge");
        Long cityId=municipality(ibge,city,uf);
        if(!cnpj.isBlank()) jdbc.update("INSERT INTO orgao(cnpj,razao_social,esfera_id,poder_id) VALUES (?,?,?,?) ON CONFLICT (cnpj) DO UPDATE SET razao_social=EXCLUDED.razao_social, esfera_id=EXCLUDED.esfera_id, poder_id=EXCLUDED.poder_id",cnpj,v(r,"orgaoEntidadeRazaoSocial"),v(r,"orgaoEntidadeEsferaId"),v(r,"orgaoEntidadePoderId"));
        Long unitId=unit(cnpj,v(r,"unidadeOrgaoCodigoUnidade"),v(r,"unidadeOrgaoNomeUnidade"),cityId);
        String subCnpj=v(r,"orgaoSubrogadoCnpj"), subUf=v(r,"unidadeSubrogadaUfSigla").toUpperCase(); if(!STATES.containsKey(subUf))subUf="";
        Long subCity=municipality(v(r,"unidadeSubrogadaCodigoIbge"),v(r,"unidadeSubrogadaMunicipioNome"),subUf);
        if(!subCnpj.isBlank())jdbc.update("INSERT INTO orgao(cnpj,razao_social,esfera_id,poder_id) VALUES (?,?,?,?) ON CONFLICT (cnpj) DO UPDATE SET razao_social=EXCLUDED.razao_social, esfera_id=EXCLUDED.esfera_id, poder_id=EXCLUDED.poder_id",subCnpj,v(r,"orgaoSubrogadoRazaoSocial"),v(r,"orgaoSubrogadoEsferaId"),v(r,"orgaoSubrogadoPoderId"));
        Long subUnit=unit(subCnpj,v(r,"unidadeSubrogadaCodigoUnidade"),v(r,"unidadeSubrogadaNomeUnidade"),subCity);
        String modality=lookup("modalidade","modalidadeIdPncp","modalidadeNome",r), status=lookup("situacao_compra","situacaoCompraIdPncp","situacaoCompraNomePncp",r), dispute=lookup("modo_disputa","modoDisputaIdPncp","modoDisputaNomePncp",r);
        String sig=v(r,"orcamentoSigilosoCodigo"), sigDesc=v(r,"orcamentoSigilosoDescricao");
        if(!sig.isBlank()&&!sigDesc.isBlank()) jdbc.update("INSERT INTO sigilo(codigo,descricao,conceito) VALUES (?,?,?) ON CONFLICT (codigo) DO UPDATE SET descricao=EXCLUDED.descricao, conceito=EXCLUDED.conceito",sig,sigDesc,sig.equals("1")?"SEM_SIGILO":sig.equals("2")?"SIGILOSO":"NAO_CLASSIFICADO");
        String legal=v(r,"amparoLegalCodigoPncp"); if(!legal.isBlank()) jdbc.update("INSERT INTO amparo_legal(codigo,nome,descricao) VALUES (?,?,?) ON CONFLICT (codigo) DO UPDATE SET nome=EXCLUDED.nome, descricao=EXCLUDED.descricao",legal,v(r,"amparoLegalNome"),v(r,"amparoLegalDescricao"));
        Integer year=integer(v(r,"anoCompraPncp"));
        Long id=jdbc.queryForObject("INSERT INTO contratacao(numero_controle_pncp,ano_pncp,numero_compra,orgao_cnpj,orgao_subrogado_cnpj,unidade_id,unidade_subrogada_id,modalidade_codigo,situacao_codigo,modo_disputa_codigo,sigilo_codigo,amparo_legal_codigo,objeto,data_publicacao,data_abertura,data_encerramento,valor_estimado,valor_homologado,arquivo_origem) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,nn(v(r,"numeroControlePNCP")),year,nn(v(r,"numeroCompra")),nn(cnpj),nn(subCnpj),unitId,subUnit,modality,status,dispute,nn(sig),nn(legal),nn(v(r,"objetoCompra")),ts(v(r,"dataPublicacaoPncp")),ts(v(r,"dataAberturaPropostaPncp")),ts(v(r,"dataEncerramentoPropostaPncp")),decimal(v(r,"valorTotalEstimado")),decimal(v(r,"valorTotalHomologado")),v(r,"_modalidadeArquivo"));
        for(String issue:issues(r)) jdbc.update("INSERT INTO problema_dado(contratacao_id,regra,descricao) VALUES (?,?,?)",id,issue.substring(0,issue.indexOf(':')),issue);
    }
    private Long municipality(String ibge,String name,String uf){
        if(name.isBlank()||uf.isBlank())return null;
        List<Long> found=ibge.isBlank()?List.of():jdbc.query("SELECT id FROM municipio WHERE codigo_ibge=?",(rs,n)->rs.getLong(1),ibge);
        if(found.isEmpty())found=jdbc.query("SELECT id FROM municipio WHERE nome=? AND estado_uf=?",(rs,n)->rs.getLong(1),name,uf);
        if(!found.isEmpty())return found.get(0);
        return jdbc.queryForObject("INSERT INTO municipio(codigo_ibge,nome,estado_uf) VALUES (?,?,?) RETURNING id",Long.class,nn(ibge),name,uf);
    }
    private Long unit(String cnpj,String code,String name,Long city){
        if(code.isBlank()&&name.isBlank()&&city==null)return null;
        List<Long> found=jdbc.query("SELECT id FROM unidade WHERE codigo_unidade IS NOT DISTINCT FROM ? AND nome IS NOT DISTINCT FROM ? AND orgao_cnpj IS NOT DISTINCT FROM CAST(? AS VARCHAR)",(rs,n)->rs.getLong(1),nn(code),nn(name),nn(cnpj));
        if(!found.isEmpty())return found.get(0);
        return jdbc.queryForObject("INSERT INTO unidade(codigo_unidade,nome,orgao_cnpj,municipio_id) VALUES (?,?,?,?) RETURNING id",Long.class,nn(code),nn(name),nn(cnpj),city);
    }
    private String lookup(String table,String codeField,String nameField,Map<String,String> r){String code=v(r,codeField),name=v(r,nameField);if(name.isBlank())return null;if(code.isBlank())code="SEM_CODIGO:"+name;jdbc.update("INSERT INTO "+table+"(codigo,nome) VALUES (?,?) ON CONFLICT (codigo) DO UPDATE SET nome=EXCLUDED.nome",code,name);return code;}
    private List<String> issues(Map<String,String> r){List<String> out=new ArrayList<>();if(v(r,"numeroControlePNCP").isBlank())out.add("IDENTIFICADOR: Número PNCP ausente");if(!STATES.containsKey(v(r,"unidadeOrgaoUfSigla").toUpperCase()))out.add("UF: UF ausente ou inválida");String code=v(r,"orcamentoSigilosoCodigo");if(v(r,"orcamentoSigilosoDescricao").isBlank()||(!code.equals("1")&&!code.equals("2")))out.add("SIGILO: Classificação ausente ou código inválido");LocalDateTime pub=date(v(r,"dataPublicacaoPncp")),open=date(v(r,"dataAberturaPropostaPncp")),end=date(v(r,"dataEncerramentoPropostaPncp"));if((pub!=null&&open!=null&&open.isBefore(pub))||(open!=null&&end!=null&&end.isBefore(open)))out.add("DATAS: Datas fora de ordem");BigDecimal amount=decimal(v(r,"valorTotalEstimado"));if(amount!=null&&amount.signum()==0)out.add("VALOR_ZERO: Valor estimado igual a zero; revisar com a fonte");return out;}
    private void seedSynonym(){jdbc.update("INSERT INTO conceito_termo(conceito,descricao) VALUES (?,?) ON CONFLICT (conceito) DO UPDATE SET descricao=EXCLUDED.descricao","ORCAMENTO_SEM_SIGILO","Orçamento declarado sem sigilo");Long id=jdbc.queryForObject("SELECT id FROM conceito_termo WHERE conceito=?",Long.class,"ORCAMENTO_SEM_SIGILO");jdbc.update("DELETE FROM termo_alias WHERE conceito_id=?",id);jdbc.update("INSERT INTO termo_alias(conceito_id,termo_origem,tipo,nivel_completude,campo_origem,evidencia) VALUES (?,?,?,?,?,?)",id,"1","CÓDIGO","CODIFICADO","orcamentoSigilosoCodigo","Código 1 observado associado à descrição Compra sem sigilo");jdbc.update("INSERT INTO termo_alias(conceito_id,termo_origem,tipo,nivel_completude,campo_origem,evidencia) VALUES (?,?,?,?,?,?)",id,"Compra sem sigilo","DESCRIÇÃO","ROTULADO","orcamentoSigilosoDescricao","Descrição observada associada ao código 1");}
    private String v(Map<String,String> r,String k){String s=r.get(k);return s==null||s.trim().equalsIgnoreCase("null")?"":s.trim();}
    private String nn(String s){return s==null||s.isBlank()?null:s;}
    private Integer integer(String s){try{return s.isBlank()?null:Integer.valueOf(s);}catch(Exception e){return null;}}
    private BigDecimal decimal(String s){try{return s.isBlank()?null:new BigDecimal(s);}catch(Exception e){return null;}}
    private Timestamp ts(String s){LocalDateTime d=date(s);return d==null?null:Timestamp.valueOf(d);}
    private LocalDateTime date(String s){if(s.isBlank())return null;try{return OffsetDateTime.parse(s).toLocalDateTime();}catch(Exception ignored){try{return LocalDateTime.parse(s);}catch(Exception ignored2){return null;}}}
}
