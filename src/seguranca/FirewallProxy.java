package seguranca;

import utils.Cores;
import utils.Mensagem;
import utils.ServicoSeguranca;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FirewallProxy implements Runnable {
    
    private static final int PORTA_ENTRADA = Integer.parseInt(
        System.getenv().getOrDefault("PROXY_TCP_PORT", "6000"));
    private static final String DATACENTER_HOST = 
        System.getenv().getOrDefault("DATACENTER_HOST", "datacenter");
    private static final int DATACENTER_PORT = Integer.parseInt(
        System.getenv().getOrDefault("DATACENTER_TCP_PORT", "6001"));
    
    private ServerSocket serverSocket;
    private final ExecutorService executor;
    private final AtomicInteger conexoesAtivas;
    private final AtomicInteger mensagensProcessadas;
    private final AtomicInteger mensagensBloqueadas;
    private boolean ativo;
    
    public FirewallProxy() {
        this.executor = Executors.newFixedThreadPool(30);
        this.conexoesAtivas = new AtomicInteger(0);
        this.mensagensProcessadas = new AtomicInteger(0);
        this.mensagensBloqueadas = new AtomicInteger(0);
        this.ativo = false;
    }
    
    public void iniciar() throws Exception {
        log("=".repeat(60));
        log(Cores.ciano("INICIANDO FIREWALL PROXY DE APLICAÇÃO"));
        log("=".repeat(60));
        
        serverSocket = new ServerSocket(PORTA_ENTRADA);
        ativo = true;
        
        log("Escutando TCP na porta " + PORTA_ENTRADA);
        log("Encaminhando para " + DATACENTER_HOST + ":" + DATACENTER_PORT);
        
        new Thread(this, "FirewallProxy").start();
        
        log(Cores.verde("Firewall Proxy iniciado com sucesso!"));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Encerrando Firewall Proxy...");
            parar();
            exibirEstatisticas();
        }));
    }
    
    public void parar() {
        ativo = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException e) { }
        }
        executor.shutdown();
    }
    
    @Override
    public void run() {
        while (ativo) {
            try {
                Socket clienteSocket = serverSocket.accept();
                conexoesAtivas.incrementAndGet();
                
                log("Nova conexão de " + clienteSocket.getInetAddress().getHostAddress());
                
                executor.execute(() -> tratarConexao(clienteSocket));
                
            } catch (Exception e) {
                if (ativo) {
                    log(Cores.vermelho("Erro ao aceitar conexão: " + e.getMessage()));
                }
            }
        }
    }
    
    private void tratarConexao(Socket clienteSocket) {
        Socket datacenterSocket = null;
        
        try {
            datacenterSocket = new Socket(DATACENTER_HOST, DATACENTER_PORT);
            log("Conectado ao Datacenter " + DATACENTER_HOST + ":" + DATACENTER_PORT);
            
            DataInputStream inCliente = new DataInputStream(clienteSocket.getInputStream());
            DataOutputStream outCliente = new DataOutputStream(clienteSocket.getOutputStream());
            
            DataInputStream inDatacenter = new DataInputStream(datacenterSocket.getInputStream());
            DataOutputStream outDatacenter = new DataOutputStream(datacenterSocket.getOutputStream());
            
            while (ativo && !clienteSocket.isClosed() && !datacenterSocket.isClosed()) {
                try {
                    int tamanho = inCliente.readInt();
                    
                    if (tamanho <= 0 || tamanho > 100000) {
                        log(Cores.vermelho("Mensagem com tamanho inválido: " + tamanho));
                        mensagensBloqueadas.incrementAndGet();
                        continue;
                    }
                    
                    byte[] dados = new byte[tamanho];
                    inCliente.readFully(dados);
                    
                    if (inspecionarMensagem(dados)) {
                        outDatacenter.writeInt(tamanho);
                        outDatacenter.write(dados);
                        outDatacenter.flush();
                        
                        mensagensProcessadas.incrementAndGet();
                        log(Cores.verde("Mensagem encaminhada (" + tamanho + " bytes)"));
                    } else {
                        mensagensBloqueadas.incrementAndGet();
                        log(Cores.vermelho("Mensagem bloqueada (inspeção falhou)"));
                        enviarLogParaIDS("PROXY_BLOQUEIO", 
                            clienteSocket.getInetAddress().getHostAddress(), 
                            "Mensagem bloqueada na inspeção");
                    }
                    
                } catch (EOFException e) {
                    break;
                } catch (SocketException e) {
                    break;
                }
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro na conexão proxy: " + e.getMessage()));
        } finally {
            conexoesAtivas.decrementAndGet();
            fecharSocket(clienteSocket);
            fecharSocket(datacenterSocket);
        }
    }
    
    private boolean inspecionarMensagem(byte[] dados) {
        try {
            if (dados.length < 10) {
                log(Cores.amarelo("Mensagem muito pequena: " + dados.length + " bytes"));
                return false;
            }
            
            if (dados.length > 50000) {
                log(Cores.amarelo("Mensagem muito grande: " + dados.length + " bytes"));
                return false;
            }
            

            return true;
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro na inspeção: " + e.getMessage()));
            return false;
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
        }
    }
    
    private void fecharSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (IOException e) { }
        }
    }
    
    public void exibirEstatisticas() {
        log("=".repeat(60));
        log("ESTATÍSTICAS DO FIREWALL PROXY");
        log("=".repeat(60));
        log("Conexões ativas:       " + conexoesAtivas.get());
        log("Mensagens processadas: " + mensagensProcessadas.get());
        log("Mensagens bloqueadas:  " + mensagensBloqueadas.get());
        log("=".repeat(60));
    }
    
    private void log(String mensagem) {
        System.out.println("[FIREWALL-PROXY] " + mensagem);
    }
    
    public static void main(String[] args) {
        try {
            FirewallProxy proxy = new FirewallProxy();
            proxy.iniciar();
            
            // Manter rodando
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println("ERRO ao iniciar Firewall Proxy: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

