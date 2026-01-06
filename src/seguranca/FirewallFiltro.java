package seguranca;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import utils.Cores;
import utils.Mensagem;

import java.io.*;
import java.net.*;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FirewallFiltro implements Runnable {
    
    private static final int PORTA_UDP_ENTRADA = Integer.parseInt(
        System.getenv().getOrDefault("FIREWALL_UDP_PORT", "5000"));
    private static final int PORTA_COMANDO = Integer.parseInt(
        System.getenv().getOrDefault("FIREWALL_CMD_PORT", "5100"));
    private static final int PORTA_HTTP = Integer.parseInt(
        System.getenv().getOrDefault("FIREWALL_HTTP_PORT", "5001"));
    
    private final Blacklist blacklist;
    private final List<EnderecoDestino> bordas;
    private final AtomicInteger indiceRoundRobin;
    private final AtomicInteger pacotesRecebidos;
    private final AtomicInteger pacotesBloqueados;
    private final AtomicInteger pacotesEncaminhados;
    private final ExecutorService executor;
    
    private final Map<String, Integer> afinidadeSensorBorda;
    private final Map<String, String> ipParaIdDispositivo;
    private final Map<String, String> idParaIpDispositivo;
    private final Map<Integer, String> ultimoIPPorBorda;
    
    private DatagramSocket socketEntrada;
    private ServerSocket socketComando;
    private HttpServer servidorHttp;
    private boolean ativo;
    
    private static class EnderecoDestino {
        String host;
        int porta;
        
        EnderecoDestino(String host, int porta) {
            this.host = host;
            this.porta = porta;
        }
    }
    
    public FirewallFiltro() {
        this.blacklist = new Blacklist();
        this.bordas = new ArrayList<>();
        this.indiceRoundRobin = new AtomicInteger(0);
        this.pacotesRecebidos = new AtomicInteger(0);
        this.pacotesBloqueados = new AtomicInteger(0);
        this.pacotesEncaminhados = new AtomicInteger(0);
        this.executor = Executors.newFixedThreadPool(20);
        this.afinidadeSensorBorda = new ConcurrentHashMap<>();
        this.ipParaIdDispositivo = new ConcurrentHashMap<>();
        this.idParaIpDispositivo = new ConcurrentHashMap<>();
        this.ultimoIPPorBorda = new ConcurrentHashMap<>();
        this.ativo = false;
        
        carregarConfiguracaoBordas();
    }
    
    private void carregarConfiguracaoBordas() {
        String bordas1 = System.getenv().getOrDefault("BORDA_1_HOST", "borda-1");
        String bordas2 = System.getenv().getOrDefault("BORDA_2_HOST", "borda-2");
        String bordas3 = System.getenv().getOrDefault("BORDA_3_HOST", "borda-3");
        int portaBorda = Integer.parseInt(System.getenv().getOrDefault("BORDA_UDP_PORT", "5000"));
        
        bordas.add(new EnderecoDestino(bordas1, portaBorda));
        bordas.add(new EnderecoDestino(bordas2, portaBorda));
        bordas.add(new EnderecoDestino(bordas3, portaBorda));
        
        log("Bordas configuradas:");
        for (int i = 0; i < bordas.size(); i++) {
            log("  " + (i+1) + ". " + bordas.get(i).host + ":" + bordas.get(i).porta);
        }
    }
    
    public void iniciar() throws Exception {
        log("=".repeat(60));
        log(Cores.ciano("INICIANDO FIREWALL FILTRO DE PACOTES"));
        log("=".repeat(60));
        
        socketEntrada = new DatagramSocket(PORTA_UDP_ENTRADA);
        log("Escutando UDP na porta " + PORTA_UDP_ENTRADA);
        
        socketComando = new ServerSocket(PORTA_COMANDO);
        log("Escutando comandos TCP na porta " + PORTA_COMANDO);
        
        servidorHttp = HttpServer.create(new InetSocketAddress(PORTA_HTTP), 0);
        servidorHttp.setExecutor(Executors.newFixedThreadPool(4));
        servidorHttp.createContext("/publickey", this::handlePublicKey);
        servidorHttp.createContext("/health", this::handleHealth);
        servidorHttp.start();
        log("Servidor HTTP na porta " + PORTA_HTTP + " (proxy de chaves)");
        
        ativo = true;
        
        new Thread(this, "FirewallFiltro-UDP").start();
        
        new Thread(this::processarComandos, "FirewallFiltro-CMD").start();
        
        log(Cores.verde("Firewall Filtro iniciado com sucesso!"));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Encerrando Firewall Filtro...");
            parar();
            exibirEstatisticas();
        }));
    }
    
    private void handlePublicKey(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String ipCliente = exchange.getRemoteAddress().getAddress().getHostAddress();
        EnderecoDestino borda = selecionarBordaComAfinidade(ipCliente);
        
        Set<String> bordasTentadas = new HashSet<>();
        String chave = obterChaveComFailover(borda, ipCliente, bordasTentadas);
        
        if (chave != null) {
            sendHttpResponse(exchange, 200, chave);
        } else {
            sendHttpResponse(exchange, 502, "Bad Gateway - Nenhuma borda disponível");
        }
    }
    
    private String obterChaveComFailover(EnderecoDestino borda, String ipCliente, Set<String> bordasTentadas) {
        bordasTentadas.add(borda.host);
        
        try {
            String urlStr = "http://" + borda.host + ":5001/publickey";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String chave = reader.readLine();
                reader.close();
                
                log("Chave pública obtida de " + borda.host + " para " + ipCliente);
                return chave;
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Borda " + borda.host + " falhou: " + e.getMessage()));
            afinidadeSensorBorda.remove(ipCliente);
            
            EnderecoDestino novoDestino = selecionarBordaParaFailover(bordasTentadas);
            if (novoDestino != null) {
                log(Cores.amarelo("FAILOVER HTTP: Tentando " + novoDestino.host));
                return obterChaveComFailover(novoDestino, ipCliente, bordasTentadas);
            }
        }
        
        return null;
    }
    
    private void handleHealth(HttpExchange exchange) throws IOException {
        sendHttpResponse(exchange, 200, "OK");
    }
    
    private void sendHttpResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    public void parar() {
        ativo = false;
        if (socketEntrada != null && !socketEntrada.isClosed()) {
            socketEntrada.close();
        }
        if (socketComando != null && !socketComando.isClosed()) {
            try { socketComando.close(); } catch (IOException e) { }
        }
        if (servidorHttp != null) {
            servidorHttp.stop(0);
        }
        executor.shutdown();
    }
    
    @Override
    public void run() {
        byte[] buffer = new byte[65535];
        
        while (ativo) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socketEntrada.receive(pacote);
                
                byte[] dados = new byte[pacote.getLength()];
                System.arraycopy(buffer, 0, dados, 0, pacote.getLength());
                
                String ipOrigem = pacote.getAddress().getHostAddress();
                int portaOrigem = pacote.getPort();
                
                pacotesRecebidos.incrementAndGet();
                
                executor.execute(() -> processarPacote(dados, ipOrigem, portaOrigem));
                
            } catch (Exception e) {
                if (ativo) {
                    log(Cores.vermelho("Erro ao receber pacote: " + e.getMessage()));
                }
            }
        }
    }
    
    private void processarPacote(byte[] dados, String ipOrigem, int portaOrigem) {
        try {
            if (blacklist.isIPBloqueado(ipOrigem)) {
                pacotesBloqueados.incrementAndGet();
                log(Cores.vermelho("BLOQUEADO: Pacote de IP " + ipOrigem + " (na blacklist)"));
                enviarLogParaIDS("BLOQUEIO_IP", ipOrigem, "Pacote bloqueado - IP na blacklist");
                return;
            }
            
            String idRemetente = extrairIdRemetente(dados);
            if (idRemetente != null && blacklist.isIDBloqueado(idRemetente)) {
                pacotesBloqueados.incrementAndGet();
                log(Cores.vermelho("BLOQUEADO: Pacote de ID " + idRemetente + " (na blacklist)"));
                enviarLogParaIDS("BLOQUEIO_ID", idRemetente, "Pacote bloqueado - ID na blacklist");
                return;
            }
            
            EnderecoDestino destino = selecionarBordaComAfinidade(ipOrigem);
            encaminharPacote(dados, destino, ipOrigem, portaOrigem);
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar pacote: " + e.getMessage()));
        }
    }
    
    private String extrairIdRemetente(byte[] dados) {
        return null;
    }
    
    private EnderecoDestino selecionarBordaComAfinidade(String ipSensor) {
        int indice = afinidadeSensorBorda.computeIfAbsent(ipSensor, ip -> {
            int novoIndice = indiceRoundRobin.getAndIncrement() % bordas.size();
            log("Afinidade criada: " + ip + " -> borda-" + (novoIndice + 1));
            return novoIndice;
        });
        return bordas.get(indice % bordas.size());
    }
    
    private EnderecoDestino selecionarBordaRoundRobin() {
        int indice = indiceRoundRobin.getAndIncrement() % bordas.size();
        return bordas.get(indice);
    }
    
    private void encaminharPacote(byte[] dados, EnderecoDestino destino, 
                                   String ipOriginal, int portaOriginal) {
        encaminharPacoteComFailover(dados, destino, ipOriginal, portaOriginal, new HashSet<>());
    }
    
    private void encaminharPacoteComFailover(byte[] dados, EnderecoDestino destino, 
                                              String ipOriginal, int portaOriginal,
                                              Set<String> bordasTentadas) {
        bordasTentadas.add(destino.host);
        
        DatagramSocket socketProxy = null;
        try {
            InetAddress enderecoBorda = InetAddress.getByName(destino.host);
            socketProxy = new DatagramSocket();
            socketProxy.setSoTimeout(5000);
            
            DatagramPacket pacoteEnvio = new DatagramPacket(dados, dados.length, enderecoBorda, destino.porta);
            socketProxy.send(pacoteEnvio);
            
            int indiceBorda = bordas.indexOf(destino);
            if (indiceBorda >= 0) {
                ultimoIPPorBorda.put(indiceBorda, ipOriginal);
            }
            
            pacotesEncaminhados.incrementAndGet();
            log(Cores.verde("-> Encaminhado: " + ipOriginal + " -> " + destino.host + ":" + destino.porta + 
                " (" + dados.length + " bytes)"));
            
            byte[] bufferResposta = new byte[65535];
            DatagramPacket pacoteResposta = new DatagramPacket(bufferResposta, bufferResposta.length);
            socketProxy.receive(pacoteResposta);
            
            byte[] dadosResposta = new byte[pacoteResposta.getLength()];
            System.arraycopy(bufferResposta, 0, dadosResposta, 0, pacoteResposta.getLength());
            
            InetAddress enderecoSensor = InetAddress.getByName(ipOriginal);
            DatagramPacket pacoteRetorno = new DatagramPacket(
                dadosResposta, dadosResposta.length, enderecoSensor, portaOriginal);
            socketEntrada.send(pacoteRetorno);
            
            log(Cores.ciano("<- Resposta: " + destino.host + " -> " + ipOriginal + ":" + portaOriginal + 
                " (" + dadosResposta.length + " bytes)"));
            
        } catch (SocketTimeoutException | PortUnreachableException e) {
            log(Cores.vermelho("Borda " + destino.host + " não respondeu. Tentando failover..."));
            afinidadeSensorBorda.remove(ipOriginal);
            
            EnderecoDestino novoDestino = selecionarBordaParaFailover(bordasTentadas);
            if (novoDestino != null) {
                log(Cores.amarelo("FAILOVER: Redirecionando para " + novoDestino.host));
                if (socketProxy != null && !socketProxy.isClosed()) {
                    socketProxy.close();
                    socketProxy = null;
                }
                encaminharPacoteComFailover(dados, novoDestino, ipOriginal, portaOriginal, bordasTentadas);
            } else {
                log(Cores.vermelho("FAILOVER FALHOU: Nenhuma borda disponível. Todas as bordas tentadas: " + bordasTentadas));
                enviarLogParaIDS("TODAS_BORDAS_FORA", ipOriginal, "Nenhuma borda respondeu");
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao encaminhar pacote: " + e.getMessage()));
        } finally {
            if (socketProxy != null && !socketProxy.isClosed()) {
                socketProxy.close();
            }
        }
    }
    
    private EnderecoDestino selecionarBordaParaFailover(Set<String> bordasTentadas) {
        for (EnderecoDestino borda : bordas) {
            if (!bordasTentadas.contains(borda.host)) {
                return borda;
            }
        }
        return null; // Todas as bordas já foram tentadas
    }
    
    private void processarComandos() {
        while (ativo) {
            try {
                Socket cliente = socketComando.accept();
                executor.execute(() -> tratarComando(cliente));
            } catch (Exception e) {
                if (ativo) {
                    log(Cores.vermelho("Erro ao aceitar comando: " + e.getMessage()));
                }
            }
        }
    }
    
    private void tratarComando(Socket cliente) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
             PrintWriter out = new PrintWriter(cliente.getOutputStream(), true)) {
            
            String linha = in.readLine();
            if (linha == null) return;
            
            log(Cores.amarelo("Comando recebido: " + linha));
            
            String[] partes = linha.split(" ");
            String comando = partes[0].toUpperCase();
            
            switch (comando) {
                case "BLOQUEAR_IP":
                    if (partes.length > 1) {
                        blacklist.bloquearIP(partes[1]);
                        out.println("OK: IP " + partes[1] + " bloqueado");
                    }
                    break;
                    
                case "BLOQUEAR_ID":
                    if (partes.length > 1) {
                        String idBloquear = partes[1];
                        blacklist.bloquearID(idBloquear);
                        String ipAssociado = idParaIpDispositivo.get(idBloquear);
                        if (ipAssociado != null) {
                            blacklist.bloquearIP(ipAssociado);
                            log(Cores.vermelho("ID " + idBloquear + " bloqueado + IP associado " + ipAssociado));
                            out.println("OK: ID " + idBloquear + " e IP " + ipAssociado + " bloqueados");
                        } else {
                            out.println("OK: ID " + idBloquear + " bloqueado (IP não mapeado)");
                        }
                    }
                    break;
                    
                case "REGISTRAR_ID":
                    if (partes.length > 2) {
                        String ipReg = partes[1];
                        String idReg = partes[2];
                        ipParaIdDispositivo.put(ipReg, idReg);
                        idParaIpDispositivo.put(idReg, ipReg);
                        log(Cores.ciano("Associação registrada: " + ipReg + " <-> " + idReg));
                        out.println("OK: Associação registrada");
                    }
                    break;
                    
                case "REGISTRAR_AUTENTICACAO":
                    if (partes.length > 2) {
                        int bordaIdx = Integer.parseInt(partes[1]) - 1;
                        String idAuth = partes[2];
                        String ipAuth = ultimoIPPorBorda.get(bordaIdx);
                        if (ipAuth != null) {
                            ipParaIdDispositivo.put(ipAuth, idAuth);
                            idParaIpDispositivo.put(idAuth, ipAuth);
                            log(Cores.ciano("Autenticação registrada: " + idAuth + " <-> " + ipAuth + " (borda-" + (bordaIdx+1) + ")"));
                            out.println("OK: " + idAuth + " associado a " + ipAuth);
                        } else {
                            out.println("WARN: IP não encontrado para borda-" + (bordaIdx+1));
                        }
                    }
                    break;
                    
                case "DESBLOQUEAR_IP":
                    if (partes.length > 1) {
                        blacklist.desbloquearIP(partes[1]);
                        out.println("OK: IP " + partes[1] + " desbloqueado");
                    }
                    break;
                    
                case "DESBLOQUEAR_ID":
                    if (partes.length > 1) {
                        blacklist.desbloquearID(partes[1]);
                        out.println("OK: ID " + partes[1] + " desbloqueado");
                    }
                    break;
                    
                case "STATUS":
                    out.println("PACOTES_RECEBIDOS=" + pacotesRecebidos.get());
                    out.println("PACOTES_BLOQUEADOS=" + pacotesBloqueados.get());
                    out.println("PACOTES_ENCAMINHADOS=" + pacotesEncaminhados.get());
                    out.println("TOTAL_BLOQUEIOS=" + blacklist.getTotalBloqueios());
                    break;
                    
                case "LISTAR_BLACKLIST":
                    out.println("IPS=" + blacklist.getIPsBloqueados());
                    out.println("IDS=" + blacklist.getIDsBloqueados());
                    break;
                    
                default:
                    out.println("ERRO: Comando desconhecido: " + comando);
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar comando: " + e.getMessage()));
        }
    }
    
    private void enviarLogParaIDS(String tipo, String origem, String mensagem) {
        String idsHost = System.getenv().getOrDefault("IDS_HOST", "ids");
        int idsPort = Integer.parseInt(System.getenv().getOrDefault("IDS_LOG_PORT", "5200"));
        
        try {
            String log = tipo + "|" + origem + "|" + mensagem + "|" + System.currentTimeMillis();
            byte[] dados = log.getBytes();
            
            DatagramSocket socket = new DatagramSocket();
            InetAddress endereco = InetAddress.getByName(idsHost);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, idsPort);
            socket.send(pacote);
            socket.close();
            
        } catch (Exception e) {
            // Silencioso - IDS pode não estar disponível
        }
    }
    
    public void exibirEstatisticas() {
        log("=".repeat(60));
        log("ESTATÍSTICAS DO FIREWALL FILTRO");
        log("=".repeat(60));
        log("Pacotes recebidos:    " + pacotesRecebidos.get());
        log("Pacotes bloqueados:   " + pacotesBloqueados.get());
        log("Pacotes encaminhados: " + pacotesEncaminhados.get());
        log("=".repeat(60));
        blacklist.exibirStatus();
    }
    
    private void log(String mensagem) {
        System.out.println("[FIREWALL-FILTRO] " + mensagem);
    }
    
    // =====================================================
    // MAIN - Entrypoint para container Docker
    // =====================================================
    public static void main(String[] args) {
        try {
            FirewallFiltro firewall = new FirewallFiltro();
            firewall.iniciar();
            
            // Manter rodando
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println("ERRO ao iniciar Firewall Filtro: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

