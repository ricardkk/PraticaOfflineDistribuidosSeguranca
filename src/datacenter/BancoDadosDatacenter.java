package datacenter;

import utils.Cores;
import utils.DadosAmbientais;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BancoDadosDatacenter {
    
    private static final String DIRETORIO = "datacenter_dados";
    private static final String DIRETORIO_LEITURAS = DIRETORIO + "/leituras";
    private static final String DIRETORIO_RELATORIOS = DIRETORIO + "/relatorios";
    
    // Usar ReplicaManager se disponível
    private static final boolean USAR_REPLICAS = 
        "true".equalsIgnoreCase(System.getenv().getOrDefault("USAR_REPLICAS", "true"));
    
    private Queue<DadosAmbientais> bufferMemoria;
    private int totalLeiturasRecebidas;
    private ReplicaManager replicaManager;
    
    public BancoDadosDatacenter() {
        this.bufferMemoria = new ConcurrentLinkedQueue<>();
        this.totalLeiturasRecebidas = 0;
        
        if (USAR_REPLICAS) {
            log("Inicializando com ReplicaManager (3 réplicas)...");
            this.replicaManager = new ReplicaManager();
        } else {
            log("Inicializando em modo simples (sem réplicas)...");
            inicializarDiretorios();
        }
    }
    
    private void inicializarDiretorios() {
        new File(DIRETORIO).mkdirs();
        new File(DIRETORIO_LEITURAS).mkdirs();
        new File(DIRETORIO_RELATORIOS).mkdirs();
    }
    
    public synchronized void salvarLeitura(DadosAmbientais dados) {
        bufferMemoria.add(dados);
        totalLeiturasRecebidas++;
        
        if (bufferMemoria.size() > 10000) {
            bufferMemoria.poll();
        }
        
        if (USAR_REPLICAS && replicaManager != null) {
            // Usar ReplicaManager para escrita replicada
            replicaManager.salvarLeitura(dados);
        } else {
            // Modo legado - escrita simples
            String nomeArquivo = DIRETORIO_LEITURAS + "/leituras_" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
            
            try (FileWriter fw = new FileWriter(nomeArquivo, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                
                File arquivo = new File(nomeArquivo);
                if (arquivo.length() == 0) {
                    pw.println(DadosAmbientais.getCSVHeader());
                }
                pw.println(dados.toCSV());
                
            } catch (IOException e) {
                log(Cores.vermelho("Erro ao salvar leitura: " + e.getMessage()));
            }
        }
    }
    
    public synchronized void salvarRelatorio(Relatorio relatorio) {
        if (USAR_REPLICAS && replicaManager != null) {
            replicaManager.salvarRelatorio(relatorio);
        } else {
            String nomeArquivo = DIRETORIO_RELATORIOS + "/" + 
                relatorio.getTipo() + "_" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
            
            try (FileWriter fw = new FileWriter(nomeArquivo, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(relatorio.toString());
                pw.println("=".repeat(80));
            } catch (IOException e) {
                log(Cores.vermelho("Erro ao salvar relatório: " + e.getMessage()));
            }
        }
    }
    
    public void simularFalhaReplica(int replicaId) {
        if (replicaManager != null) {
            replicaManager.simularFalha(replicaId);
        }
    }
    
    public void recuperarReplica(int replicaId) {
        if (replicaManager != null) {
            replicaManager.recuperarReplica(replicaId);
        }
    }
    
    public void restaurarReplica(int replicaId) {
        recuperarReplica(replicaId);
    }
    
    public void exibirStatusReplicas() {
        if (replicaManager != null) {
            replicaManager.exibirStatus();
        }
    }
    
    public String getStatusReplicas() {
        if (replicaManager == null) {
            return "ReplicaManager não configurado";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== STATUS DAS RÉPLICAS ===\n\n");
        sb.append("Réplicas ativas: ").append(replicaManager.getReplicasAtivas()).append("/3\n");
        sb.append("Quórum: ").append(replicaManager.temQuorum() ? "SIM" : "NÃO").append("\n\n");
        
        // Detalhes seriam adicionados aqui (simplificado para HTTP)
        sb.append("Use os endpoints:\n");
        sb.append("  GET /admin/falha-replica/{1-3}     - Simular falha\n");
        sb.append("  GET /admin/restaurar-replica/{1-3} - Restaurar réplica\n");
        
        return sb.toString();
    }
    
    public ReplicaManager getReplicaManager() {
        return replicaManager;
    }
    
    private void log(String mensagem) {
        System.out.println("[BANCO-DATACENTER] " + mensagem);
    }
    
    public List<DadosAmbientais> obterTodasLeituras() {
        return new ArrayList<>(bufferMemoria);
    }
    
    public List<DadosAmbientais> obterLeiturasRecentes(int quantidade) {
        List<DadosAmbientais> resultado = new ArrayList<>();
        int count = 0;
        
        Iterator<DadosAmbientais> iterator = bufferMemoria.iterator();
        while (iterator.hasNext() && count < quantidade) {
            resultado.add(iterator.next());
            count++;
        }
        
        return resultado;
    }
    
    public Map<String, List<DadosAmbientais>> obterLeiturasPorDispositivo() {
        Map<String, List<DadosAmbientais>> mapa = new HashMap<>();
        
        for (DadosAmbientais dados : bufferMemoria) {
            String id = dados.getDispositivoId();
            mapa.computeIfAbsent(id, k -> new ArrayList<>()).add(dados);
        }
        
        return mapa;
    }
    
    public Map<String, List<DadosAmbientais>> obterLeiturasPorLocalizacao() {
        Map<String, List<DadosAmbientais>> mapa = new HashMap<>();
        
        for (DadosAmbientais dados : bufferMemoria) {
            String loc = dados.getLocalizacao();
            mapa.computeIfAbsent(loc, k -> new ArrayList<>()).add(dados);
        }
        
        return mapa;
    }
    
    public int getTotalLeituras() {
        return bufferMemoria.size();
    }
    
    public int getTotalLeiturasRecebidas() {
        return totalLeiturasRecebidas;
    }
    
    public DadosAmbientais obterUltimaLeitura() {
        Iterator<DadosAmbientais> iterator = bufferMemoria.iterator();
        DadosAmbientais ultima = null;
        while (iterator.hasNext()) {
            ultima = iterator.next();
        }
        return ultima;
    }
}

