package datacenter;

import utils.Cores;
import utils.DadosAmbientais;
import utils.Mensagem;
import utils.ServicoSeguranca;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReceptorTCP implements Runnable {
    
    private int porta;
    private ServerSocket serverSocket;
    private KeyPair parChavesDatacenter;
    private BancoDadosDatacenter bancoDados;
    private ExecutorService poolConexoes;
    private boolean ativo;
    private AtomicInteger contadorMensagens;
    
    public ReceptorTCP(int porta, KeyPair parChavesDatacenter, BancoDadosDatacenter bancoDados) {
        this.porta = porta;
        this.parChavesDatacenter = parChavesDatacenter;
        this.bancoDados = bancoDados;
        this.poolConexoes = Executors.newFixedThreadPool(5);
        this.ativo = false;
        this.contadorMensagens = new AtomicInteger(0);
    }
    
    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(porta);
        ativo = true;
        new Thread(this, "ReceptorTCP").start();
        log(Cores.verde("Iniciado na porta " + porta));
    }
    
    public void parar() {
        ativo = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignora
        }
        poolConexoes.shutdown();
        log("Encerrado");
    }
    
    @Override
    public void run() {
        log("Aguardando conexões da borda...");
        
        while (ativo) {
            try {
                Socket socket = serverSocket.accept();
                log(Cores.ciano("→ Conexão recebida de: " + socket.getInetAddress().getHostAddress()));
                poolConexoes.execute(() -> processarConexao(socket));
                
            } catch (IOException e) {
                if (ativo) {
                    log("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }
    
    private void processarConexao(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            
            while (ativo && !socket.isClosed()) {
                int tamanho = in.readInt();
                byte[] dados = new byte[tamanho];
                in.readFully(dados);
                
                processarMensagem(dados);
            }
            
        } catch (EOFException e) {
            log("Conexão encerrada pela borda");
        } catch (IOException e) {
            if (ativo) {
                log("Erro na conexão: " + e.getMessage());
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignora
            }
        }
    }
    
    private void processarMensagem(byte[] dadosCriptografados) {
        try {
            log(Cores.magenta("Mensagem criptografada recebida: " + 
                bytesParaHex(dadosCriptografados, 32)));
            
            byte[] dadosDescriptografados = ServicoSeguranca.descriptografar(
                dadosCriptografados, 
                parChavesDatacenter.getPrivate()
            );
            
            Mensagem mensagem = Mensagem.desserializar(dadosDescriptografados);
            contadorMensagens.incrementAndGet();
            
            if (mensagem.getTipo() == Mensagem.Tipo.DADOS_SENSOR) {
                DadosAmbientais dados = (DadosAmbientais) mensagem.getConteudo();
                bancoDados.salvarLeitura(dados);
                
                log(String.format("Leitura armazenada: %s - CO2:%.1fppm Temp:%.1f°C", 
                    dados.getDispositivoId(), dados.getCo2(), dados.getTemperatura()));
                
            } else {
                log("Tipo de mensagem desconhecido: " + mensagem.getTipo());
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar mensagem: " + e.getMessage()));
        }
    }
    
    public KeyPair getParChaves() {
        return parChavesDatacenter;
    }
    
    public int getTotalMensagensProcessadas() {
        return contadorMensagens.get();
    }
    
    private void log(String mensagem) {
        System.out.println("[RECEPTOR-TCP] " + mensagem);
    }
    
    private String bytesParaHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) return "";
        
        StringBuilder hex = new StringBuilder();
        int limite = Math.min(bytes.length, maxBytes);
        
        for (int i = 0; i < limite; i++) {
            hex.append(String.format("%02X", bytes[i]));
        }
        
        if (bytes.length > maxBytes) {
            hex.append("... (").append(bytes.length).append(" bytes total)");
        }
        
        return hex.toString();
    }
}

