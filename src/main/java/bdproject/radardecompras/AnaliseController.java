package bdproject.radardecompras;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AnaliseController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dataDir = Path.of("contratacoes");
    private final JdbcTemplate jdbc;

    public AnaliseController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/relacional")
    public Map<String, Object> relationalStats() {
        return Map.of(
                "contratacoes", jdbc.queryForObject("SELECT COUNT(*) FROM contratacao", Long.class),
                "orgaos", jdbc.queryForObject("SELECT COUNT(*) FROM orgao", Long.class),
                "unidades", jdbc.queryForObject("SELECT COUNT(*) FROM unidade", Long.class),
                "municipios", jdbc.queryForObject("SELECT COUNT(*) FROM municipio", Long.class),
                "problemas", jdbc.queryForObject("SELECT COUNT(*) FROM problema_dado", Long.class),
                "aliases", jdbc.queryForObject("SELECT COUNT(*) FROM termo_alias", Long.class));
    }

    @GetMapping("/api/contratacoes")
    public Map<String, Object> contratacoes() throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        List<Map<String, Object>> fontes = new ArrayList<>();
        for (String file : List.of("contratacoesModalidadeTres.csv", "contratacoesModalidadeCinco.csv.csv", "contratacoesModalidadeSeis.csv", "contratacoesModalidadeSete.csv")) {
            Path path = dataDir.resolve(file);
            if (!Files.exists(path)) continue;
            String content = Files.readString(path, StandardCharsets.UTF_8).replace("\uFEFF", "");
            int first = 0;
            while (first < content.length() && Character.isWhitespace(content.charAt(first))) first++;
            long expected = -1;
            if (first < content.length() && content.charAt(first) == '{') {
                JsonNode root = mapper.readTree(content);
                JsonNode result = root.path("resultado");
                if (root.path("totalRegistros").canConvertToLong()) expected = root.path("totalRegistros").asLong();
                if (result.isArray()) for (JsonNode item : result) rows.add(flatten(item, file));
            } else {
                List<List<String>> records = parseCsv(content);
                if (!records.isEmpty()) {
                    List<String> headers = records.get(0);
                    for (int i = 1; i < records.size(); i++) {
                        List<String> values = records.get(i);
                        Map<String, String> row = new LinkedHashMap<>();
                        for (int c = 0; c < headers.size(); c++) {
                            String key = headers.get(c).replaceFirst("^resultado__", "");
                            row.put(key, c < values.size() ? values.get(c) : "");
                        }
                        row.put("_modalidadeArquivo", file);
                        rows.add(row);
                    }
                }
            }
            long found = rows.stream().filter(r -> file.equals(r.get("_modalidadeArquivo"))).count();
            fontes.add(Map.of("arquivo", file, "registrosNoArquivo", found, "totalInformadoNaFonte", expected));
        }
        return Map.of("registros", rows, "fontes", fontes);
    }

    private Map<String, String> flatten(JsonNode node, String file) {
        Map<String, String> row = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> row.put(e.getKey(), e.getValue().isNull() ? "" : e.getValue().asText()));
        row.put("_modalidadeArquivo", file);
        return row;
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) { record.add(field.toString()); field.setLength(0); }
            else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                record.add(field.toString()); field.setLength(0);
                if (record.stream().anyMatch(value -> !value.isBlank())) records.add(record);
                record = new ArrayList<>();
            } else field.append(ch);
        }
        if (field.length() > 0 || !record.isEmpty()) { record.add(field.toString()); if (record.stream().anyMatch(value -> !value.isBlank())) records.add(record); }
        return records;
    }
}
