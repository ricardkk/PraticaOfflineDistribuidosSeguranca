package cliente;

import java.io.*;
import java.net.Socket;

public class ClienteHTTP {
    
    private String host;
    private int porta;
    private String tokenAutenticacao;
    
    public ClienteHTTP(String host, int porta) {
        this.host = host;
        this.porta = porta;
        this.tokenAutenticacao = null;
    }
    
    public void setTokenAutenticacao(String token) {
        this.tokenAutenticacao = token;
    }
    
    public byte[] requisicaoPOST(String caminho, byte[] dadosCriptografados) throws Exception {
        try (Socket socket = new Socket(host, porta);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            StringBuilder header = new StringBuilder();
            header.append("POST ").append(caminho).append(" HTTP/1.1\r\n");
            header.append("Host: ").append(host).append("\r\n");
            header.append("Content-Type: application/octet-stream\r\n");
            header.append("Content-Length: ").append(dadosCriptografados.length).append("\r\n");
            header.append("Connection: close\r\n");
            header.append("\r\n");
            
            out.write(header.toString().getBytes());
            out.write(dadosCriptografados);
            out.flush();
            
            String statusLine = in.readLine();
            if (statusLine == null) throw new IOException("Sem resposta do servidor");
            
            if (!statusLine.contains("200")) {
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    errorBody.append(line).append("\n");
                }
                throw new IOException("Erro do Servidor (" + statusLine + "):\n" + errorBody.toString());
            }
            
            String linha;
            while ((linha = in.readLine()) != null) {
                if (linha.isEmpty()) break;
            }
            
            StringBuilder corpo = new StringBuilder();
            while ((linha = in.readLine()) != null) {
                corpo.append(linha);
            }
            
            String base64Clean = corpo.toString().replaceAll("[\\s\\r\\n]+", "");
            
            if (base64Clean.isEmpty()) {
                return new byte[0];
            }
            
            return java.util.Base64.getDecoder().decode(base64Clean);
        }
    }
    
    public String requisicaoGET(String caminho) throws Exception {
        try (Socket socket = new Socket(host, porta);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            out.println("GET " + caminho + " HTTP/1.1");
            out.println("Host: " + host);
            
            if (tokenAutenticacao != null) {
                out.println("Authorization: Bearer " + tokenAutenticacao);
            }
            
            out.println("Connection: close");
            out.println();
            out.flush();
            
            StringBuilder resposta = new StringBuilder();
            String linha;
            boolean isBody = false;
            
            while ((linha = in.readLine()) != null) {
                if (isBody) {
                    resposta.append(linha).append("\n");
                } else if (linha.isEmpty()) {
                    isBody = true;
                }
            }
            
            return resposta.toString();
        }
    }
    
    public String requisicaoGETComTimeout(String caminho, int timeoutMs) throws Exception {
        try (Socket socket = new Socket(host, porta)) {
            socket.setSoTimeout(timeoutMs);
            
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                
                out.println("GET " + caminho + " HTTP/1.1");
                out.println("Host: " + host);
                
                if (tokenAutenticacao != null) {
                    out.println("Authorization: Bearer " + tokenAutenticacao);
                }
                
                out.println("Connection: close");
                out.println();
                out.flush();
                
                StringBuilder resposta = new StringBuilder();
                String linha;
                boolean isBody = false;
                
                while ((linha = in.readLine()) != null) {
                    if (isBody) {
                        resposta.append(linha).append("\n");
                    } else if (linha.isEmpty()) {
                        isBody = true;
                    }
                }
                
                return resposta.toString();
            }
        }
    }
    
    public boolean testarConexao() {
        try (Socket socket = new Socket(host, porta)) {
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}


