package borda;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import utils.Cores;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.Executors;

public class ServidorChavesBorda {
    
    private final int porta;
    private final PublicKey chavePublica;
    private HttpServer servidor;
    private boolean ativo;
    
    public ServidorChavesBorda(int porta, PublicKey chavePublica) {
        this.porta = porta;
        this.chavePublica = chavePublica;
        this.ativo = false;
    }
    
    public void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        
        servidor.createContext("/publickey", this::handlePublicKey);
        servidor.createContext("/health", this::handleHealth);
        
        servidor.start();
        ativo = true;
        
        log(Cores.verde("Servidor de chaves iniciado na porta " + porta));
        log("  - GET /publickey - Retorna chave pública em Base64");
        log("  - GET /health    - Health check");
    }
    
    public void parar() {
        if (servidor != null && ativo) {
            servidor.stop(0);
            ativo = false;
            log("Servidor de chaves encerrado");
        }
    }
    
    private void handlePublicKey(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        try {
            byte[] chaveBytes = chavePublica.getEncoded();
            String chaveBase64 = Base64.getEncoder().encodeToString(chaveBytes);
            
            log("Chave pública solicitada por " + exchange.getRemoteAddress());
            sendResponse(exchange, 200, chaveBase64);
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao retornar chave: " + e.getMessage()));
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }
    
    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        sendResponse(exchange, 200, "OK");
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    public boolean isAtivo() {
        return ativo;
    }
    
    private void log(String mensagem) {
        System.out.println("[BORDA-KEYS] " + mensagem);
    }
}

