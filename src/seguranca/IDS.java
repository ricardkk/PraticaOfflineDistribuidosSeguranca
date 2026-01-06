package seguranca;

import utils.Cores;
import utils.DadosAmbientais;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IDS implements Runnable {
    
    private static final int PORTA_LOG = Integer.parseInt(
        System.getenv().getOrDefault("IDS_LOG_PORT", "5200"));
    private static final String FIREWALL_HOST = 
        System.getenv().getOrDefault("FIREWALL_HOST", "firewall-filtro");
    private static final int FIREWALL_CMD_PORT = Integer.parseInt(
        System.getenv().getOrDefault("FIREWALL_CMD_PORT", "5100"));
    
    private static final double LIMITE_TEMPERATURA_MAX = 100.0;
    private static final double LIMITE_TEMPERATURA_MIN = -50.0;
    private static final double LIMITE_CO2_MAX = 10000.0;
    private static final double LIMITE_UMIDADE_MAX = 100.0;
    private static final double LIMITE_UV_MAX = 20.0;
    private static final int LIMITE_VIOLACOES_BLOQUEIO = 3;
    private static final long JANELA_TEMPO_MS = 60000;
    
    private DatagramSocket socketLog;
    private final ExecutorService executor;
    private final AtomicInteger logsRecebidos;
    private final AtomicInteger intrusoesDetectadas;
    private final AtomicInteger bloqueiosExecutados;
    private final Map<String, List<Long>> violacoesPorOrigem;
    private final Set<String> jaBloqueados;
    private final List<AlertaSeguranca> alertasRecentes;
    private boolean ativo;
    
    private static class AlertaSeguranca {
        String tipo;
        String origem;
        String descricao;
        LocalDateTime timestamp;
        
        AlertaSeguranca(String tipo, String origem, String descricao) {
            this.tipo = tipo;
            this.origem = origem;
            this.descricao = descricao;
            this.timestamp = LocalDateTime.now();
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s - %s: %s",
                timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                tipo, origem, descricao);
        }
    }
    
    public IDS() {
        this.executor = Executors.newFixedThreadPool(10);
        this.logsRecebidos = new AtomicInteger(0);
        this.intrusoesDetectadas = new AtomicInteger(0);
        this.bloqueiosExecutados = new AtomicInteger(0);
        this.violacoesPorOrigem = new ConcurrentHashMap<>();
        this.jaBloqueados = ConcurrentHashMap.newKeySet();
        this.alertasRecentes = new CopyOnWriteArrayList<>();
        this.ativo = false;
    }
    
    public void iniciar() throws Exception {
        log("=".repeat(60));
        log(Cores.ciano("INICIANDO SISTEMA DE DETECÇÃO DE INTRUSÃO (IDS)"));
        log("=".repeat(60));
        
        socketLog = new DatagramSocket(PORTA_LOG);
        ativo = true;
        
        log("Escutando logs UDP na porta " + PORTA_LOG);
        log("Firewall configurado: " + FIREWALL_HOST + ":" + FIREWALL_CMD_PORT);
        log("");
        log("Limites de detecção:");
        log("  - Temperatura: " + LIMITE_TEMPERATURA_MIN + "°C a " + LIMITE_TEMPERATURA_MAX + "°C");
        log("  - CO2: máximo " + LIMITE_CO2_MAX + " ppm");
        log("  - UV: máximo " + LIMITE_UV_MAX);
        log("  - Violações para bloqueio: " + LIMITE_VIOLACOES_BLOQUEIO + " em " + (JANELA_TEMPO_MS/1000) + "s");
        
        new Thread(this, "IDS-Receiver").start();
        executor.execute(this::limpezaPeriodica);
        
        log(Cores.verde("IDS iniciado com sucesso!"));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Encerrando IDS...");
            parar();
            exibirEstatisticas();
        }));
    }
    
    public void parar() {
        ativo = false;
        if (socketLog != null && !socketLog.isClosed()) {
            socketLog.close();
        }
        executor.shutdown();
    }
    
    @Override
    public void run() {
        byte[] buffer = new byte[65535];
        
        while (ativo) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socketLog.receive(pacote);
                
                String mensagem = new String(pacote.getData(), 0, pacote.getLength());
                String ipOrigem = pacote.getAddress().getHostAddress();
                
                logsRecebidos.incrementAndGet();
                executor.execute(() -> processarLog(mensagem, ipOrigem));
                
            } catch (Exception e) {
                if (ativo) {
                    log(Cores.vermelho("Erro ao receber log: " + e.getMessage()));
                }
            }
        }
    }
    
    private void processarLog(String mensagem, String ipOrigem) {
        try {
            String[] partes = mensagem.split("\\|");
            if (partes.length < 3) return;
            
            String tipo = partes[0];
            String origem = partes[1];
            String descricao = partes[2];
            
            log(Cores.amarelo("Log recebido: " + tipo + " de " + origem));
            
            switch (tipo) {
                case "DADOS_ANOMALOS":
                    analisarDadosAnomalos(origem, descricao);
                    break;
                case "AUTENTICACAO_FALHA":
                    registrarTentativaFalha(origem, descricao);
                    break;
                case "BLOQUEIO_IP":
                case "BLOQUEIO_ID":
                    registrarAlerta(tipo, origem, descricao);
                    break;
                case "PROXY_BLOQUEIO":
                    registrarAlerta(tipo, origem, descricao);
                    verificarNecessidadeBloqueio(origem);
                    break;
                default:
                    log("Log: " + mensagem);
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar log: " + e.getMessage()));
        }
    }
    
    private void analisarDadosAnomalos(String origem, String descricao) {
        boolean anomaliaDetectada = false;
        StringBuilder detalhes = new StringBuilder();
        
        try {
            String[] campos = descricao.split(",");
            
            for (String campo : campos) {
                String[] kv = campo.split(":");
                if (kv.length != 2) continue;
                
                String nome = kv[0].trim();
                double valor = Double.parseDouble(kv[1].trim());
                
                switch (nome.toLowerCase()) {
                    case "temperatura":
                    case "temp":
                        if (valor > LIMITE_TEMPERATURA_MAX || valor < LIMITE_TEMPERATURA_MIN) {
                            anomaliaDetectada = true;
                            detalhes.append("Temperatura absurda: ").append(valor).append("°C; ");
                        }
                        break;
                    case "co2":
                        if (valor > LIMITE_CO2_MAX) {
                            anomaliaDetectada = true;
                            detalhes.append("CO2 absurdo: ").append(valor).append("ppm; ");
                        }
                        break;
                    case "uv":
                        if (valor > LIMITE_UV_MAX) {
                            anomaliaDetectada = true;
                            detalhes.append("UV absurdo: ").append(valor).append("; ");
                        }
                        break;
                    case "umidade":
                        if (valor > LIMITE_UMIDADE_MAX || valor < 0) {
                            anomaliaDetectada = true;
                            detalhes.append("Umidade absurda: ").append(valor).append("%; ");
                        }
                        break;
                }
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao analisar dados: " + e.getMessage()));
        }
        
        if (anomaliaDetectada) {
            log(Cores.vermelho("ANOMALIA DETECTADA de " + origem + ": " + detalhes));
            registrarViolacao(origem);
            registrarAlerta("ANOMALIA", origem, detalhes.toString());
            verificarNecessidadeBloqueio(origem);
        }
    }
    
    private void registrarTentativaFalha(String origem, String descricao) {
        log(Cores.amarelo("Tentativa de autenticação falha: " + origem));
        registrarViolacao(origem);
        registrarAlerta("AUTH_FALHA", origem, descricao);
        verificarNecessidadeBloqueio(origem);
    }
    
    private void registrarViolacao(String origem) {
        violacoesPorOrigem.computeIfAbsent(origem, k -> new CopyOnWriteArrayList<>())
            .add(System.currentTimeMillis());
    }
    
    private void verificarNecessidadeBloqueio(String origem) {
        if (jaBloqueados.contains(origem)) return;
        
        List<Long> violacoes = violacoesPorOrigem.get(origem);
        if (violacoes == null) return;
        
        long agora = System.currentTimeMillis();
        long limiteInferior = agora - JANELA_TEMPO_MS;
        
        long violacoesRecentes = violacoes.stream()
            .filter(t -> t > limiteInferior)
            .count();
        
        if (violacoesRecentes >= LIMITE_VIOLACOES_BLOQUEIO) {
            log(Cores.vermelho("!!! INTRUSÃO DETECTADA: " + origem + 
                " (" + violacoesRecentes + " violações em " + (JANELA_TEMPO_MS/1000) + "s)"));
            
            intrusoesDetectadas.incrementAndGet();
            executarBloqueio(origem);
        }
    }
    
    private void executarBloqueio(String origem) {
        try {
            log(Cores.vermelho("EXECUTANDO BLOQUEIO: " + origem));
            
            Socket socket = new Socket(FIREWALL_HOST, FIREWALL_CMD_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String comando;
            if (origem.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                comando = "BLOQUEAR_IP " + origem;
            } else {
                comando = "BLOQUEAR_ID " + origem;
            }
            
            out.println(comando);
            String resposta = in.readLine();
            socket.close();
            
            if (resposta != null && resposta.startsWith("OK")) {
                jaBloqueados.add(origem);
                bloqueiosExecutados.incrementAndGet();
                log(Cores.verde("Bloqueio executado com sucesso: " + resposta));
                registrarAlerta("BLOQUEIO_IDS", origem, "Bloqueado pelo IDS após detecção de intrusão");
            } else {
                log(Cores.vermelho("Falha no bloqueio: " + resposta));
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao executar bloqueio: " + e.getMessage()));
        }
    }
    
    private void registrarAlerta(String tipo, String origem, String descricao) {
        AlertaSeguranca alerta = new AlertaSeguranca(tipo, origem, descricao);
        alertasRecentes.add(alerta);
        
        while (alertasRecentes.size() > 100) {
            alertasRecentes.remove(0);
        }
    }
    
    private void limpezaPeriodica() {
        while (ativo) {
            try {
                Thread.sleep(60000);
                
                long agora = System.currentTimeMillis();
                long limiteInferior = agora - JANELA_TEMPO_MS * 5;
                
                for (List<Long> lista : violacoesPorOrigem.values()) {
                    lista.removeIf(t -> t < limiteInferior);
                }
                
                violacoesPorOrigem.entrySet().removeIf(e -> e.getValue().isEmpty());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public boolean analisarDados(String dispositivoId, DadosAmbientais dados) {
        boolean anomalia = false;
        StringBuilder detalhes = new StringBuilder();
        
        if (dados.getTemperatura() > LIMITE_TEMPERATURA_MAX || 
            dados.getTemperatura() < LIMITE_TEMPERATURA_MIN) {
            anomalia = true;
            detalhes.append("Temp:").append(dados.getTemperatura()).append(",");
        }
        
        if (dados.getCo2() > LIMITE_CO2_MAX) {
            anomalia = true;
            detalhes.append("CO2:").append(dados.getCo2()).append(",");
        }
        
        if (dados.getUv() > LIMITE_UV_MAX) {
            anomalia = true;
            detalhes.append("UV:").append(dados.getUv()).append(",");
        }
        
        if (dados.getUmidade() > LIMITE_UMIDADE_MAX || dados.getUmidade() < 0) {
            anomalia = true;
            detalhes.append("Umidade:").append(dados.getUmidade()).append(",");
        }
        
        if (anomalia) {
            analisarDadosAnomalos(dispositivoId, detalhes.toString());
        }
        
        return !anomalia;
    }
    
    public void exibirEstatisticas() {
        log("=".repeat(60));
        log("ESTATÍSTICAS DO IDS");
        log("=".repeat(60));
        log("Logs recebidos:       " + logsRecebidos.get());
        log("Intrusões detectadas: " + intrusoesDetectadas.get());
        log("Bloqueios executados: " + bloqueiosExecutados.get());
        log("Origens monitoradas:  " + violacoesPorOrigem.size());
        log("=".repeat(60));
        
        if (!alertasRecentes.isEmpty()) {
            log("ÚLTIMOS ALERTAS:");
            int inicio = Math.max(0, alertasRecentes.size() - 10);
            for (int i = inicio; i < alertasRecentes.size(); i++) {
                log("  " + alertasRecentes.get(i));
            }
        }
    }
    
    private void log(String mensagem) {
        System.out.println("[IDS] " + mensagem);
    }
    
    public static void main(String[] args) {
        try {
            IDS ids = new IDS();
            ids.iniciar();
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            System.err.println("ERRO ao iniciar IDS: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
