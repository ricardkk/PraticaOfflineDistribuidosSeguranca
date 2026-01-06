package dispositivo;

import utils.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Random;

public class DispositivoSensor implements Runnable {
    
    private String id;
    private String nome;
    private String localizacao;
    private KeyPair parChaves;
    private PublicKey chavePublicaBorda;
    private ClienteUDP clienteUDP;
    private boolean autenticado;
    private boolean ativo;
    private long intervaloEnvio;
    private String tag;
    
    public DispositivoSensor(String id, String nome, String localizacao, 
                            KeyPair parChaves, String hostBorda, int portaBorda) throws Exception {
        this.id = id;
        this.nome = nome;
        this.localizacao = localizacao;
        this.parChaves = parChaves;
        this.clienteUDP = new ClienteUDP(hostBorda, portaBorda);
        this.autenticado = false;
        this.ativo = false;
        int baseIntervalo = Integer.parseInt(System.getenv().getOrDefault("SENSOR_INTERVALO_MS", "8000"));
        this.intervaloEnvio = baseIntervalo + new Random().nextInt(2000);
        this.tag = "[DISPOSITIVO-" + id + "]";
    }
    
    public boolean autenticar(byte[] chavePublicaBordaBytes) {
        try {
            log("Iniciando autenticação...");
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.chavePublicaBorda = keyFactory.generatePublic(new X509EncodedKeySpec(chavePublicaBordaBytes));
            
            Credenciais credenciais = new Credenciais(id, nome, parChaves.getPublic());
            Mensagem mensagem = new Mensagem(Mensagem.Tipo.AUTENTICACAO, id, credenciais);
            
            byte[] mensagemSerializada = mensagem.serializar();
            byte[] mensagemCriptografada = ServicoSeguranca.criptografar(mensagemSerializada, chavePublicaBorda);
            
            log(Cores.magenta("Mensagem criptografada: " + Cores.bytesParaHex(mensagemCriptografada, 32)));
            log("Enviando credenciais para borda...");
            clienteUDP.enviar(mensagemCriptografada);
            
            log("Aguardando resposta da borda...");
            byte[] respostaBytes = clienteUDP.receberResposta(65535);
            log("Resposta recebida, descriptografando...");
            
            byte[] respostaDescriptografada = ServicoSeguranca.descriptografar(respostaBytes, parChaves.getPrivate());
            Mensagem resposta = Mensagem.desserializar(respostaDescriptografada);
            
            if (resposta.getTipo() == Mensagem.Tipo.ACK) {
                autenticado = true;
                log(Cores.verde("Autenticacao bem-sucedida!"));
                return true;
            } else {
                log(Cores.vermelho("Autenticacao falhou: " + resposta.getConteudo()));
                return false;
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("ERRO na autenticacao: " + e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }
    
    public void iniciar() {
        if (!autenticado) {
            log("Dispositivo não autenticado. Execute autenticar() primeiro.");
            return;
        }
        ativo = true;
        new Thread(this).start();
    }
    
    public void parar() {
        ativo = false;
        log("Parando dispositivo...");
    }
    
    @Override
    public void run() {
        log("Iniciando coleta e envio de dados (intervalo: " + intervaloEnvio + "ms)");
        
        while (ativo) {
            try {
                DadosAmbientais dados = GeradorDadosAmbientais.gerar(id, localizacao);
                enviarDados(dados);
                Thread.sleep(intervaloEnvio);
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log("ERRO ao enviar dados: " + e.getMessage());
            }
        }
        
        clienteUDP.fechar();
        log("Dispositivo encerrado.");
    }
    
    private void enviarDados(DadosAmbientais dados) throws Exception {
        Mensagem mensagem = new Mensagem(Mensagem.Tipo.DADOS_SENSOR, id, dados);
        byte[] mensagemSerializada = mensagem.serializar();
        byte[] mensagemCriptografada = ServicoSeguranca.criptografar(mensagemSerializada, chavePublicaBorda);
        
        log(Cores.magenta("Mensagem criptografada: " + Cores.bytesParaHex(mensagemCriptografada, 32)));
        clienteUDP.enviar(mensagemCriptografada);
        log("Dados enviados - CO2: " + String.format("%.1f", dados.getCo2()) + "ppm, " +
            "Temp: " + String.format("%.1f", dados.getTemperatura()) + "°C");
    }
    
    private void log(String mensagem) {
        System.out.println(tag + " " + mensagem);
    }
    
    public String getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAutenticado() { return autenticado; }
    public boolean isAtivo() { return ativo; }
    
    public void enviarDadosMaliciosos(DadosAmbientais dados) throws Exception {
        if (!autenticado) {
            log("Dispositivo não autenticado.");
            return;
        }
        enviarDados(dados);
    }
    
    public boolean autenticarViaRede(String hostBorda, int portaHttp) {
        int maxTentativas = 15;
        int intervaloMs = 3000;
        
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                String urlStr = "http://" + hostBorda + ":" + portaHttp + "/publickey";
                log("Tentativa " + tentativa + "/" + maxTentativas + " - Obtendo chave de " + urlStr);
                
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
                    log(Cores.verde("Chave pública da Borda obtida com sucesso"));
                    
                    return autenticar(chaveBytes);
                } else {
                    log(Cores.amarelo("Borda retornou código " + responseCode));
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
        
        log(Cores.vermelho("Não foi possível obter chave da Borda após " + maxTentativas + " tentativas"));
        return false;
    }
}

