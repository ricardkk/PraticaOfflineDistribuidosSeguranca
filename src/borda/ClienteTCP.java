package borda;

import utils.DadosAmbientais;
import utils.Mensagem;
import utils.ServicoSeguranca;
import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClienteTCP implements Runnable {
    
    private String hostDatacenter;
    private int portaDatacenter;
    private PublicKey chavePublicaDatacenter;
    private BlockingQueue<DadosAmbientais> filaEnvio;
    private boolean ativo;
    private Socket socket;
    
    public ClienteTCP(String hostDatacenter, int portaDatacenter) {
        this.hostDatacenter = hostDatacenter;
        this.portaDatacenter = portaDatacenter;
        this.filaEnvio = new LinkedBlockingQueue<>();
        this.ativo = false;
    }
    
    public void setChavePublicaDatacenter(PublicKey chavePublica) {
        this.chavePublicaDatacenter = chavePublica;
    }
    
    public void adicionarParaEnvio(DadosAmbientais dados) {
        try {
            filaEnvio.put(dados);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void iniciar() {
        ativo = true;
        new Thread(this, "ClienteTCP-Datacenter").start();
    }
    
    public void parar() {
        ativo = false;
        fecharConexao();
    }
    
    @Override
    public void run() {
        log("Iniciado - aguardando conexão com datacenter...");
        
        while (ativo) {
            try {
                if (socket == null || socket.isClosed()) {
                    tentarConectar();
                }
                
                if (socket != null && socket.isConnected()) {
                    DadosAmbientais dados = filaEnvio.take();
                    enviarDados(dados);
                } else {
                    Thread.sleep(5000);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log("Erro: " + e.getMessage());
                fecharConexao();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        fecharConexao();
        log("Encerrado");
    }
    
    private void tentarConectar() {
        try {
            socket = new Socket(hostDatacenter, portaDatacenter);
            log("Conectado ao datacenter: " + hostDatacenter + ":" + portaDatacenter);
        } catch (IOException e) {
            log("Datacenter não disponível (será tentado novamente)");
            socket = null;
        }
    }
    
    private void enviarDados(DadosAmbientais dados) throws Exception {
        Mensagem mensagem = new Mensagem(Mensagem.Tipo.DADOS_SENSOR, "BORDA", dados);
        byte[] mensagemSerializada = mensagem.serializar();
        
        byte[] mensagemCriptografada;
        if (chavePublicaDatacenter != null) {
            mensagemCriptografada = ServicoSeguranca.criptografar(
                mensagemSerializada, chavePublicaDatacenter);
            log("[TCP-DATACENTER] Mensagem criptografada: " + 
                bytesParaHex(mensagemCriptografada, 32));
        } else {
            mensagemCriptografada = mensagemSerializada;
            log("[TCP-DATACENTER] AVISO: Mensagem NÃO criptografada (chave pública não configurada)");
        }
        
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(mensagemCriptografada.length);
        out.write(mensagemCriptografada);
        out.flush();
        
        log("Dados enviados ao datacenter: " + dados.getDispositivoId());
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
    
    private void fecharConexao() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignora
            }
        }
    }
    
    public int getTamanhoFila() {
        return filaEnvio.size();
    }
    
    public boolean isConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    private void log(String mensagem) {
        System.out.println("[TCP-DATACENTER] " + mensagem);
    }
}

