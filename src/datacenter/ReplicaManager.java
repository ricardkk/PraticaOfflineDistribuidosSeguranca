package datacenter;

import utils.Cores;
import utils.DadosAmbientais;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicaManager {
    
    private static final String BASE_DIR = System.getenv().getOrDefault("DATA_DIR", "datacenter_dados");
    private static final int NUM_REPLICAS = 3;
    
    private final List<Replica> replicas;
    private final AtomicInteger escritasSucesso;
    private final AtomicInteger escritasFalha;
    private final AtomicInteger leiturasSucesso;
    
    private static class Replica {
        int id;
        String diretorio;
        boolean ativa;
        AtomicInteger escritas;
        AtomicInteger leituras;
        LocalDateTime ultimaEscrita;
        LocalDateTime ultimaFalha;
        
        Replica(int id, String diretorio) {
            this.id = id;
            this.diretorio = diretorio;
            this.ativa = true;
            this.escritas = new AtomicInteger(0);
            this.leituras = new AtomicInteger(0);
            this.ultimaEscrita = null;
            this.ultimaFalha = null;
        }
    }
    
    public ReplicaManager() {
        this.replicas = new ArrayList<>();
        this.escritasSucesso = new AtomicInteger(0);
        this.escritasFalha = new AtomicInteger(0);
        this.leiturasSucesso = new AtomicInteger(0);
        
        inicializarReplicas();
    }
    
    private void inicializarReplicas() {
        log("Inicializando " + NUM_REPLICAS + " réplicas...");
        
        for (int i = 1; i <= NUM_REPLICAS; i++) {
            String dir = BASE_DIR + "/replica" + i;
            Replica replica = new Replica(i, dir);
            
            new File(dir + "/leituras").mkdirs();
            new File(dir + "/relatorios").mkdirs();
            
            replicas.add(replica);
            log(Cores.verde("  Réplica " + i + " inicializada em " + dir));
        }
        
        log("Todas as réplicas inicializadas.");
    }
    
    public synchronized boolean salvarLeitura(DadosAmbientais dados) {
        int sucessos = 0;
        int falhas = 0;
        
        String nomeArquivo = "leituras_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        
        for (Replica replica : replicas) {
            if (!replica.ativa) {
                continue;
            }
            
            try {
                String caminho = replica.diretorio + "/leituras/" + nomeArquivo;
                
                File arquivo = new File(caminho);
                boolean novoArquivo = !arquivo.exists() || arquivo.length() == 0;
                
                try (FileWriter fw = new FileWriter(caminho, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    
                    if (novoArquivo) {
                        pw.println(DadosAmbientais.getCSVHeader());
                    }
                    pw.println(dados.toCSV());
                }
                
                replica.escritas.incrementAndGet();
                replica.ultimaEscrita = LocalDateTime.now();
                sucessos++;
                
            } catch (IOException e) {
                log(Cores.vermelho("Erro ao escrever na réplica " + replica.id + ": " + e.getMessage()));
                falhas++;
            }
        }
        
        if (sucessos > 0) {
            escritasSucesso.incrementAndGet();
            log(Cores.verde("Leitura salva em " + sucessos + "/" + getReplicasAtivas() + " réplicas"));
        } else {
            escritasFalha.incrementAndGet();
            log(Cores.vermelho("FALHA: Nenhuma réplica disponível para escrita!"));
        }
        
        return sucessos > 0;
    }
    
    public synchronized boolean salvarRelatorio(Relatorio relatorio) {
        int sucessos = 0;
        
        String nomeArquivo = relatorio.getTipo() + "_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
        
        for (Replica replica : replicas) {
            if (!replica.ativa) continue;
            
            try {
                String caminho = replica.diretorio + "/relatorios/" + nomeArquivo;
                
                try (FileWriter fw = new FileWriter(caminho, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(relatorio.toString());
                    pw.println("=".repeat(80));
                }
                
                replica.escritas.incrementAndGet();
                replica.ultimaEscrita = LocalDateTime.now();
                sucessos++;
                
            } catch (IOException e) {
                log(Cores.vermelho("Erro ao salvar relatório na réplica " + replica.id));
            }
        }
        
        return sucessos > 0;
    }
    
    public List<String> lerLeituras() {
        for (Replica replica : replicas) {
            if (!replica.ativa) continue;
            
            try {
                String dir = replica.diretorio + "/leituras";
                File diretorio = new File(dir);
                
                if (!diretorio.exists()) continue;
                
                List<String> leituras = new ArrayList<>();
                
                File[] arquivos = diretorio.listFiles((d, name) -> name.endsWith(".csv"));
                if (arquivos == null) continue;
                
                for (File arquivo : arquivos) {
                    try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
                        String linha;
                        boolean primeiraLinha = true;
                        while ((linha = br.readLine()) != null) {
                            if (primeiraLinha) {
                                primeiraLinha = false;
                                continue;
                            }
                            leituras.add(linha);
                        }
                    }
                }
                
                replica.leituras.incrementAndGet();
                leiturasSucesso.incrementAndGet();
                
                log("Leituras obtidas da réplica " + replica.id + " (" + leituras.size() + " registros)");
                return leituras;
                
            } catch (Exception e) {
                log(Cores.amarelo("Falha ao ler da réplica " + replica.id + ", tentando próxima..."));
            }
        }
        
        log(Cores.vermelho("Nenhuma réplica disponível para leitura!"));
        return new ArrayList<>();
    }
    
    public void simularFalha(int replicaId) {
        if (replicaId < 1 || replicaId > NUM_REPLICAS) {
            log(Cores.vermelho("ID de réplica inválido: " + replicaId));
            return;
        }
        
        Replica replica = replicas.get(replicaId - 1);
        
        if (!replica.ativa) {
            log(Cores.amarelo("Réplica " + replicaId + " já está inativa"));
            return;
        }
        
        replica.ativa = false;
        replica.ultimaFalha = LocalDateTime.now();
        
        log(Cores.vermelho("!!! FALHA SIMULADA na Réplica " + replicaId));
        log("Réplicas ativas: " + getReplicasAtivas() + "/" + NUM_REPLICAS);
        
        if (getReplicasAtivas() == 0) {
            log(Cores.vermelho("ALERTA CRÍTICO: Nenhuma réplica disponível!"));
        }
    }
    
    public void recuperarReplica(int replicaId) {
        if (replicaId < 1 || replicaId > NUM_REPLICAS) {
            log(Cores.vermelho("ID de réplica inválido: " + replicaId));
            return;
        }
        
        Replica replica = replicas.get(replicaId - 1);
        
        if (replica.ativa) {
            log(Cores.amarelo("Réplica " + replicaId + " já está ativa"));
            return;
        }
        
        replica.ativa = true;
        
        log(Cores.verde("Réplica " + replicaId + " RECUPERADA!"));
        log("Réplicas ativas: " + getReplicasAtivas() + "/" + NUM_REPLICAS);
        
        sincronizarReplica(replicaId);
    }
    
    private void sincronizarReplica(int replicaId) {
        log(Cores.ciano("Sincronizando réplica " + replicaId + "..."));
        
        Replica replicaDestino = replicas.get(replicaId - 1);
        
        Replica replicaFonte = null;
        for (Replica r : replicas) {
            if (r.ativa && r.id != replicaId) {
                replicaFonte = r;
                break;
            }
        }
        
        if (replicaFonte == null) {
            log(Cores.amarelo("Nenhuma réplica fonte disponível para sincronização"));
            return;
        }
        
        try {
            File fonteDir = new File(replicaFonte.diretorio + "/leituras");
            File destinoDir = new File(replicaDestino.diretorio + "/leituras");
            
            if (fonteDir.exists() && fonteDir.isDirectory()) {
                File[] arquivos = fonteDir.listFiles();
                if (arquivos != null) {
                    for (File arquivo : arquivos) {
                        copiarArquivo(arquivo, new File(destinoDir, arquivo.getName()));
                    }
                }
            }
            
            log(Cores.verde("Sincronização da réplica " + replicaId + " concluída!"));
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro na sincronização: " + e.getMessage()));
        }
    }
    
    private void copiarArquivo(File fonte, File destino) throws IOException {
        try (InputStream in = new FileInputStream(fonte);
             OutputStream out = new FileOutputStream(destino)) {
            byte[] buffer = new byte[8192];
            int bytesLidos;
            while ((bytesLidos = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesLidos);
            }
        }
    }
    
    public int getReplicasAtivas() {
        return (int) replicas.stream().filter(r -> r.ativa).count();
    }
    
    public boolean temQuorum() {
        return getReplicasAtivas() > NUM_REPLICAS / 2;
    }
    
    public void exibirStatus() {
        log("=".repeat(60));
        log("STATUS DO REPLICA MANAGER");
        log("=".repeat(60));
        log("Réplicas ativas: " + getReplicasAtivas() + "/" + NUM_REPLICAS);
        log("Quórum: " + (temQuorum() ? "SIM" : "NÃO"));
        log("Escritas com sucesso: " + escritasSucesso.get());
        log("Escritas com falha:   " + escritasFalha.get());
        log("Leituras realizadas:  " + leiturasSucesso.get());
        log("");
        
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
        
        for (Replica r : replicas) {
            String status = r.ativa ? Cores.verde("ATIVA") : Cores.vermelho("INATIVA");
            log("Réplica " + r.id + ": " + status);
            log("  Diretório: " + r.diretorio);
            log("  Escritas:  " + r.escritas.get());
            log("  Leituras:  " + r.leituras.get());
            if (r.ultimaEscrita != null) {
                log("  Última escrita: " + r.ultimaEscrita.format(fmt));
            }
            if (r.ultimaFalha != null) {
                log("  Última falha:   " + r.ultimaFalha.format(fmt));
            }
        }
        
        log("=".repeat(60));
    }
    
    private void log(String mensagem) {
        System.out.println("[REPLICA-MANAGER] " + mensagem);
    }
}
