package borda;

import utils.Cores;
import utils.ServicoSeguranca;
import utils.ServicoDescoberta;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class ServidorBorda {
    
    private static final int PORTA_UDP = Integer.parseInt(
        System.getenv().getOrDefault("BORDA_UDP_PORT", "5000"));
    private static final int PORTA_HTTP_CHAVES = Integer.parseInt(
        System.getenv().getOrDefault("BORDA_HTTP_PORT", "5001"));
    private static final String HOST_DATACENTER_TCP = 
        System.getenv().getOrDefault("DATACENTER_HOST", "localhost");
    private static final int PORTA_DATACENTER_TCP = Integer.parseInt(
        System.getenv().getOrDefault("DATACENTER_TCP_PORT", "6000"));
    private static final String HOST_DATACENTER_HTTP = 
        System.getenv().getOrDefault("DATACENTER_HTTP_HOST", "datacenter");
    private static final int PORTA_DATACENTER_HTTP = Integer.parseInt(
        System.getenv().getOrDefault("DATACENTER_HTTP_PORT", "8080"));
    private static final String SHARED_KEY_SEED = 
        System.getenv().getOrDefault("SHARED_KEY_SEED", null);
    
    private KeyPair parChavesBorda;
    private BancoDadosBorda bancoDados;
    private AutenticadorBorda autenticador;
    private AnalisadorRapido analisadorRapido;
    private ClienteTCP clienteTCP;
    private ReceptorUDP receptorUDP;
    private ServidorChavesBorda servidorChaves;
    private boolean ativo;
    
    public ServidorBorda() throws Exception {
        log("Inicializando Servidor Borda...");
        
        if (SHARED_KEY_SEED != null && !SHARED_KEY_SEED.isEmpty()) {
            log("Gerando par de chaves RSA compartilhadas (semente: " + SHARED_KEY_SEED.substring(0, 4) + "...)");
            this.parChavesBorda = ServicoSeguranca.gerarParChavesRSADeterministico(SHARED_KEY_SEED);
        } else {
            log("Gerando par de chaves RSA único...");
            this.parChavesBorda = ServicoSeguranca.gerarParChavesRSA();
        }
        
        log("Inicializando banco de dados local...");
        this.bancoDados = new BancoDadosBorda();
        
        log("Inicializando autenticador...");
        this.autenticador = new AutenticadorBorda(bancoDados);
        
        log("Inicializando analisador rápido...");
        this.analisadorRapido = new AnalisadorRapido(bancoDados);
        
        log("Inicializando cliente TCP para datacenter...");
        this.clienteTCP = new ClienteTCP(HOST_DATACENTER_TCP, PORTA_DATACENTER_TCP);
        
        log("Inicializando receptor UDP...");
        this.receptorUDP = new ReceptorUDP(
            PORTA_UDP,
            parChavesBorda,
            autenticador,
            bancoDados,
            analisadorRapido,
            clienteTCP
        );
        
        log("Inicializando servidor de chaves HTTP...");
        this.servidorChaves = new ServidorChavesBorda(PORTA_HTTP_CHAVES, parChavesBorda.getPublic());
        
        this.ativo = false;
    }
    
    public void iniciar() throws Exception {
        if (ativo) {
            log("Servidor já está em execução!");
            return;
        }
        
        log("=".repeat(60));
        log("INICIANDO SERVIDOR BORDA");
        log("=".repeat(60));
        
        // Iniciar servidor de chaves para dispositivos
        servidorChaves.iniciar();
        
        log(Cores.verde("[DISCOVERY] Consultando serviço de nomes..."));
        log(Cores.verde("[DISCOVERY] Datacenter TCP: " + HOST_DATACENTER_TCP + ":" + PORTA_DATACENTER_TCP));
        log(Cores.verde("[DISCOVERY] Datacenter HTTP: " + HOST_DATACENTER_HTTP + ":" + PORTA_DATACENTER_HTTP));
        
        // Obter chave pública do datacenter via HTTP
        log("Obtendo chave pública do Datacenter...");
        PublicKey chaveDatacenter = obterChavePublicaDatacenter();
        if (chaveDatacenter != null) {
            clienteTCP.setChavePublicaDatacenter(chaveDatacenter);
            log(Cores.verde("Chave pública do Datacenter configurada"));
        } else {
            log(Cores.amarelo("AVISO: Não foi possível obter chave do Datacenter. Mensagens não serão criptografadas."));
        }
        
        analisadorRapido.iniciar();
        clienteTCP.iniciar();
        receptorUDP.iniciar();
        
        ativo = true;
        
        log(Cores.verde("Servidor Borda iniciado com sucesso!"));
        log("  - Porta UDP (dispositivos): " + PORTA_UDP);
        log("  - Porta HTTP (chaves): " + PORTA_HTTP_CHAVES);
        log("  - Datacenter TCP: " + HOST_DATACENTER_TCP + ":" + PORTA_DATACENTER_TCP);
        log("  - Datacenter HTTP: " + HOST_DATACENTER_HTTP + ":" + PORTA_DATACENTER_HTTP);
        log("  - Aguardando conexões...");
    }
    
    private PublicKey obterChavePublicaDatacenter() {
        int maxTentativas = 10;
        int intervaloMs = 3000;
        
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                String urlStr = "http://" + HOST_DATACENTER_HTTP + ":" + PORTA_DATACENTER_HTTP + "/publickey";
                log("Tentativa " + tentativa + "/" + maxTentativas + " - Conectando a " + urlStr);
                
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String chaveBase64 = reader.readLine();
                    reader.close();
                    
                    byte[] chaveBytes = Base64.getDecoder().decode(chaveBase64.trim());
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(chaveBytes);
                    
                    return keyFactory.generatePublic(keySpec);
                } else {
                    log(Cores.amarelo("Datacenter retornou código " + responseCode));
                }
                
            } catch (Exception e) {
                log(Cores.amarelo("Falha ao obter chave: " + e.getMessage()));
            }
            
            if (tentativa < maxTentativas) {
                try {
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return null;
    }
    
    public void parar() {
        if (!ativo) return;
        
        log("Encerrando Servidor Borda...");
        
        receptorUDP.parar();
        analisadorRapido.parar();
        clienteTCP.parar();
        servidorChaves.parar();
        
        ServicoDescoberta.getInstancia().removerServico("BORDA-MAIN");
        
        ativo = false;
        
        exibirEstatisticas();
        log("Servidor Borda encerrado.");
    }
    
    public void exibirEstatisticas() {
        log("=".repeat(60));
        log("ESTATÍSTICAS DO SERVIDOR BORDA");
        log("=".repeat(60));
        log("Total de dispositivos autenticados: " + autenticador.getTotalDispositivosAtivos());
        log("Total de leituras armazenadas: " + bancoDados.getTotalLeituras());
        log("Total de mensagens processadas: " + receptorUDP.getTotalMensagensProcessadas());
        log("Fila de análise rápida: " + analisadorRapido.getTamanhoFila());
        log("Fila de envio para datacenter: " + clienteTCP.getTamanhoFila());
        log("Conectado ao datacenter: " + (clienteTCP.isConectado() ? "SIM" : "NÃO"));
        log("=".repeat(60));
    }
    
    public byte[] getChavePublicaBytes() {
        return parChavesBorda.getPublic().getEncoded();
    }
    
    public KeyPair getParChaves() {
        return parChavesBorda;
    }
    
    public boolean isAtivo() {
        return ativo;
    }
    
    public void configurarChaveDatacenter(byte[] chavePublicaBytes) throws Exception {
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.PublicKey chavePublica = keyFactory.generatePublic(
            new java.security.spec.X509EncodedKeySpec(chavePublicaBytes)
        );
        clienteTCP.setChavePublicaDatacenter(chavePublica);
        log("Chave pública do datacenter configurada");
    }
    
    private void log(String mensagem) {
        System.out.println("[BORDA] " + mensagem);
    }
    
    public static void main(String[] args) {
        try {
            ServidorBorda servidor = new ServidorBorda();
            servidor.iniciar();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidor.parar();
            }));
            
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println("ERRO ao iniciar Servidor Borda: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

