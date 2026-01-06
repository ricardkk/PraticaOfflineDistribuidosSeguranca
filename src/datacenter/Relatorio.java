package datacenter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Relatorio implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum TipoRelatorio {
        QUALIDADE_AR,
        MAPA_POLUICAO,
        ILHAS_CALOR,
        RISCO_SAUDE,
        CONFORTO_AMBIENTAL
    }
    
    private TipoRelatorio tipo;
    private String titulo;
    private String conteudo;
    private LocalDateTime dataGeracao;
    
    public Relatorio(TipoRelatorio tipo, String titulo, String conteudo) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.conteudo = conteudo;
        this.dataGeracao = LocalDateTime.now();
    }
    
    public TipoRelatorio getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataGeracao() { return dataGeracao; }
    
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return String.format("\n=== %s ===\nGerado em: %s\n\n%s\n", 
            titulo, dataGeracao.format(formatter), conteudo);
    }
    
    public String toJSON() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return String.format(
            "{\"tipo\":\"%s\",\"titulo\":\"%s\",\"dataGeracao\":\"%s\",\"conteudo\":\"%s\"}",
            tipo, titulo, dataGeracao.format(formatter), 
            conteudo.replace("\n", "\\n").replace("\"", "\\\"")
        );
    }
}

