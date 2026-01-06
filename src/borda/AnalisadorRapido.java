package borda;

import utils.Cores;
import utils.DadosAmbientais;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AnalisadorRapido implements Runnable {
    
    // Limiares normais de alerta
    private static final double LIMIAR_CO2 = 1600;
    private static final double LIMIAR_CO = 40;
    private static final double LIMIAR_NO2 = 160;
    private static final double LIMIAR_SO2 = 80;
    private static final double LIMIAR_PM25 = 120;
    private static final double LIMIAR_PM10 = 200;
    private static final double LIMIAR_RUIDO = 85;
    private static final double LIMIAR_UV = 9;
    
    // Limiares de intrusão (valores absurdos)
    private static final double LIMIAR_INTRUSAO_TEMP_MAX = 100.0;
    private static final double LIMIAR_INTRUSAO_TEMP_MIN = -50.0;
    private static final double LIMIAR_INTRUSAO_CO2 = 10000.0;
    private static final double LIMIAR_INTRUSAO_UV = 20.0;
    private static final double LIMIAR_INTRUSAO_UMIDADE = 100.0;
    
    // Configuração do IDS
    private static final String IDS_HOST = System.getenv().getOrDefault("IDS_HOST", "ids");
    private static final int IDS_PORT = Integer.parseInt(
        System.getenv().getOrDefault("IDS_LOG_PORT", "5200"));
    
    private BlockingQueue<DadosAmbientais> filaAnalise;
    private BancoDadosBorda bancoDados;
    private boolean ativo;
    
    public AnalisadorRapido(BancoDadosBorda bancoDados) {
        this.filaAnalise = new LinkedBlockingQueue<>();
        this.bancoDados = bancoDados;
        this.ativo = false;
    }
    
    public void adicionarParaAnalise(DadosAmbientais dados) {
        try {
            filaAnalise.put(dados);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void iniciar() {
        ativo = true;
        new Thread(this, "AnalisadorRapido").start();
    }
    
    public void parar() {
        ativo = false;
    }
    
    @Override
    public void run() {
        log("Iniciado");
        
        while (ativo) {
            try {
                DadosAmbientais dados = filaAnalise.take();
                analisar(dados);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log("Encerrado");
    }
    
    private void analisar(DadosAmbientais dados) {
        // Primeiro, verificar intrusão (valores absurdos)
        if (verificarIntrusao(dados)) {
            return; // Dados reportados ao IDS, não processar mais
        }
        
        // Análise normal de alertas
        StringBuilder alertas = new StringBuilder();
        int contadorAlertas = 0;
        
        if (dados.getCo2() > LIMIAR_CO2) {
            alertas.append(String.format("CO2 ALTO: %.1fppm (limite: %.1fppm) ", 
                dados.getCo2(), LIMIAR_CO2));
            contadorAlertas++;
        }
        
        if (dados.getCo() > LIMIAR_CO) {
            alertas.append(String.format("CO ALTO: %.1fppm (limite: %.1fppm) ", 
                dados.getCo(), LIMIAR_CO));
            contadorAlertas++;
        }
        
        if (dados.getNo2() > LIMIAR_NO2) {
            alertas.append(String.format("NO2 ALTO: %.1fppb (limite: %.1fppb) ", 
                dados.getNo2(), LIMIAR_NO2));
            contadorAlertas++;
        }
        
        if (dados.getSo2() > LIMIAR_SO2) {
            alertas.append(String.format("SO2 ALTO: %.1fppb (limite: %.1fppb) ", 
                dados.getSo2(), LIMIAR_SO2));
            contadorAlertas++;
        }
        
        if (dados.getPm25() > LIMIAR_PM25) {
            alertas.append(String.format("PM2.5 ALTO: %.1fμg/m³ (limite: %.1fμg/m³) ", 
                dados.getPm25(), LIMIAR_PM25));
            contadorAlertas++;
        }
        
        if (dados.getPm10() > LIMIAR_PM10) {
            alertas.append(String.format("PM10 ALTO: %.1fμg/m³ (limite: %.1fμg/m³) ", 
                dados.getPm10(), LIMIAR_PM10));
            contadorAlertas++;
        }
        
        if (dados.getRuido() > LIMIAR_RUIDO) {
            alertas.append(String.format("RUÍDO ALTO: %.1fdB (limite: %.1fdB) ", 
                dados.getRuido(), LIMIAR_RUIDO));
            contadorAlertas++;
        }
        
        if (dados.getUv() > LIMIAR_UV) {
            alertas.append(String.format("UV ALTO: %.1f (limite: %.1f) ", 
                dados.getUv(), LIMIAR_UV));
            contadorAlertas++;
        }
        
        if (contadorAlertas > 0) {
            String mensagemBase = String.format("ALERTA [%s @ %s]: %s", 
                dados.getDispositivoId(), dados.getLocalizacao(), alertas.toString());
            
            String mensagemColorida;
            if (contadorAlertas >= 3) {
                mensagemColorida = Cores.vermelhoBold(mensagemBase + " (CRÍTICO)");
            } else {
                mensagemColorida = Cores.laranjaBold(mensagemBase);
            }
            
            log(mensagemColorida);
            bancoDados.salvarAlerta(mensagemBase);
        }
    }
    
    private boolean verificarIntrusao(DadosAmbientais dados) {
        StringBuilder anomalias = new StringBuilder();
        boolean intrusaoDetectada = false;
        
        // Verificar temperatura absurda
        if (dados.getTemperatura() > LIMIAR_INTRUSAO_TEMP_MAX || 
            dados.getTemperatura() < LIMIAR_INTRUSAO_TEMP_MIN) {
            anomalias.append("Temperatura:").append(dados.getTemperatura()).append(",");
            intrusaoDetectada = true;
        }
        
        // Verificar CO2 absurdo
        if (dados.getCo2() > LIMIAR_INTRUSAO_CO2) {
            anomalias.append("CO2:").append(dados.getCo2()).append(",");
            intrusaoDetectada = true;
        }
        
        // Verificar UV absurdo
        if (dados.getUv() > LIMIAR_INTRUSAO_UV) {
            anomalias.append("UV:").append(dados.getUv()).append(",");
            intrusaoDetectada = true;
        }
        
        // Verificar umidade impossível
        if (dados.getUmidade() > LIMIAR_INTRUSAO_UMIDADE || dados.getUmidade() < 0) {
            anomalias.append("Umidade:").append(dados.getUmidade()).append(",");
            intrusaoDetectada = true;
        }
        
        if (intrusaoDetectada) {
            log(Cores.vermelhoBold("!!! POSSÍVEL INTRUSÃO DETECTADA de " + dados.getDispositivoId() + " !!!"));
            log(Cores.vermelhoBold("    Dados absurdos: " + anomalias.toString()));
            
            // Enviar log para o IDS
            enviarLogParaIDS(dados.getDispositivoId(), anomalias.toString());
            
            // Salvar alerta localmente
            bancoDados.salvarAlerta("INTRUSÃO DETECTADA: " + dados.getDispositivoId() + " - " + anomalias);
        }
        
        return intrusaoDetectada;
    }
    
    private void enviarLogParaIDS(String dispositivoId, String anomalias) {
        try {
            String mensagem = "DADOS_ANOMALOS|" + dispositivoId + "|" + anomalias + "|" + System.currentTimeMillis();
            byte[] dadosEnvio = mensagem.getBytes();
            
            DatagramSocket socket = new DatagramSocket();
            InetAddress endereco = InetAddress.getByName(IDS_HOST);
            DatagramPacket pacote = new DatagramPacket(dadosEnvio, dadosEnvio.length, endereco, IDS_PORT);
            socket.send(pacote);
            socket.close();
            
            log(Cores.amarelo("Log enviado para IDS: " + dispositivoId));
            
        } catch (Exception e) {
            log(Cores.amarelo("Aviso: Não foi possível enviar log para IDS - " + e.getMessage()));
        }
    }
    
    public int getTamanhoFila() {
        return filaAnalise.size();
    }
    
    private void log(String mensagem) {
        System.out.println("[ANALISADOR-RAPIDO] " + mensagem);
    }
}

