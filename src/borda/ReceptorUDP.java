package borda;

import utils.Cores;
import utils.DadosAmbientais;
import utils.Mensagem;
import utils.ServicoSeguranca;
import java.io.*;
import java.net.*;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReceptorUDP implements Runnable {
    
    private int porta;
    private DatagramSocket socket;
    private KeyPair parChavesBorda;
    private AutenticadorBorda autenticador;
    private BancoDadosBorda bancoDados;
    private AnalisadorRapido analisadorRapido;
    private ClienteTCP clienteTCP;
    private ExecutorService poolProcessamento;
    private boolean ativo;
    private AtomicInteger contadorMensagens;
    
    private static final String FIREWALL_HOST = System.getenv().getOrDefault("FIREWALL_HOST", "firewall-filtro");
    private static final int FIREWALL_CMD_PORT = Integer.parseInt(System.getenv().getOrDefault("FIREWALL_CMD_PORT", "5100"));
    private static final int BORDA_ID = Integer.parseInt(System.getenv().getOrDefault("BORDA_ID", "1"));
    
    public ReceptorUDP(int porta, KeyPair parChavesBorda, 
                       AutenticadorBorda autenticador,
                       BancoDadosBorda bancoDados,
                       AnalisadorRapido analisadorRapido,
                       ClienteTCP clienteTCP) {
        this.porta = porta;
        this.parChavesBorda = parChavesBorda;
        this.autenticador = autenticador;
        this.bancoDados = bancoDados;
        this.analisadorRapido = analisadorRapido;
        this.clienteTCP = clienteTCP;
        this.poolProcessamento = Executors.newFixedThreadPool(10);
        this.ativo = false;
        this.contadorMensagens = new AtomicInteger(0);
    }
    
    public void iniciar() throws Exception {
        socket = new DatagramSocket(porta);
        ativo = true;
        new Thread(this, "ReceptorUDP").start();
        log("Iniciado na porta " + porta);
    }
    
    public void parar() {
        ativo = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        poolProcessamento.shutdown();
        log("Encerrado");
    }
    
    @Override
    public void run() {
        byte[] buffer = new byte[65535];
        log("Loop de recepção UDP iniciado...");
        
        while (ativo) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);
                
                byte[] dadosRecebidos = new byte[pacote.getLength()];
                System.arraycopy(buffer, 0, dadosRecebidos, 0, pacote.getLength());
                
                InetAddress enderecoRemetente = pacote.getAddress();
                int portaRemetente = pacote.getPort();
                
                log("Pacote UDP recebido de " + enderecoRemetente + ":" + portaRemetente + 
                    " (" + pacote.getLength() + " bytes) - delegando para processamento");
                
                poolProcessamento.execute(() -> 
                    processarMensagem(dadosRecebidos, enderecoRemetente, portaRemetente)
                );
                
            } catch (Exception e) {
                if (ativo) {
                    log(Cores.vermelho("Erro ao receber pacote: " + e.getMessage()));
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void processarMensagem(byte[] dadosCriptografados, 
                                   InetAddress endereco, int porta) {
        try {
            log("Recebendo mensagem de " + endereco + ":" + porta + " (" + dadosCriptografados.length + " bytes)");
            log(Cores.magenta("Mensagem criptografada recebida: " + Cores.bytesParaHex(dadosCriptografados, 32)));
            
            byte[] dadosDescriptografados = ServicoSeguranca.descriptografar(
                dadosCriptografados, parChavesBorda.getPrivate()
            );
            
            Mensagem mensagem = Mensagem.desserializar(dadosDescriptografados);
            contadorMensagens.incrementAndGet();
            
            log("Mensagem descriptografada. Tipo: " + mensagem.getTipo() + ", Remetente: " + mensagem.getRemetente());
            
            if (mensagem.getTipo() == Mensagem.Tipo.AUTENTICACAO) {
                processarAutenticacao(mensagem, endereco, porta);
                
            } else if (mensagem.getTipo() == Mensagem.Tipo.DADOS_SENSOR) {
                processarDadosSensor(mensagem, endereco, porta);
                
            } else {
                log("Tipo de mensagem desconhecido: " + mensagem.getTipo());
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar mensagem: " + e.getMessage()));
            e.printStackTrace();
        }
    }
    
    private void processarAutenticacao(Mensagem mensagem, 
                                      InetAddress endereco, int porta) {
        try {
            log("Processando autenticação de: " + mensagem.getRemetente());
            Mensagem resposta = autenticador.autenticar(mensagem);
            
            log("Criptografando resposta para enviar...");
            byte[] respostaSerializada = resposta.serializar();
            byte[] respostaCriptografada = ServicoSeguranca.criptografar(
                respostaSerializada, 
                autenticador.obterChavePublicaDispositivo(mensagem.getRemetente())
            );
            
            log(Cores.magenta("Mensagem criptografada: " + Cores.bytesParaHex(respostaCriptografada, 32)));
            
            DatagramPacket pacoteResposta = new DatagramPacket(
                respostaCriptografada, 
                respostaCriptografada.length, 
                endereco, 
                porta
            );
            
            socket.send(pacoteResposta);
            log("Resposta de autenticação enviada para " + mensagem.getRemetente() + " (" + endereco + ":" + porta + ")");
            
            if (resposta.getTipo() == Mensagem.Tipo.ACK) {
                notificarFirewallAutenticacao(mensagem.getRemetente());
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar autenticação: " + e.getMessage()));
            e.printStackTrace();
        }
    }
    
    private void notificarFirewallAutenticacao(String dispositivoId) {
        try {
            Socket socket = new Socket(FIREWALL_HOST, FIREWALL_CMD_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("REGISTRAR_AUTENTICACAO " + BORDA_ID + " " + dispositivoId);
            String resposta = in.readLine();
            socket.close();
            
            log(Cores.ciano("Notificação ao Firewall: " + resposta));
        } catch (Exception e) {
            log(Cores.amarelo("Aviso: Não foi possível notificar Firewall - " + e.getMessage()));
        }
    }
    
    private void processarDadosSensor(Mensagem mensagem, InetAddress endereco, int porta) {
        try {
            String dispositivoId = mensagem.getRemetente();
            
            if (!autenticador.isDispositivoAutenticado(dispositivoId)) {
                log(Cores.vermelho("Dispositivo nao autenticado tentou enviar dados: " + dispositivoId));
                return;
            }
            
            DadosAmbientais dados = (DadosAmbientais) mensagem.getConteudo();
            
            bancoDados.salvarLeitura(dados);
            analisadorRapido.adicionarParaAnalise(dados);
            clienteTCP.adicionarParaEnvio(dados);
            
            log(Cores.ciano("→ Dados recebidos de " + dispositivoId + " - " +
                String.format("CO2:%.1fppm Temp:%.1f°C", dados.getCo2(), dados.getTemperatura())));
            
            // Enviar ACK de volta para o Firewall
            Mensagem confirmacao = new Mensagem(Mensagem.Tipo.ACK, "BORDA", "Dados recebidos");
            byte[] respostaSerializada = confirmacao.serializar();
            
            java.security.PublicKey chaveDispositivo = autenticador.obterChavePublicaDispositivo(dispositivoId);
            byte[] respostaFinal;
            if (chaveDispositivo != null) {
                respostaFinal = ServicoSeguranca.criptografar(respostaSerializada, chaveDispositivo);
            } else {
                respostaFinal = respostaSerializada;
            }
            
            DatagramPacket pacoteResposta = new DatagramPacket(
                respostaFinal, 
                respostaFinal.length, 
                endereco, 
                porta
            );
            socket.send(pacoteResposta);
            
        } catch (Exception e) {
            log("Erro ao processar dados do sensor: " + e.getMessage());
        }
    }
    
    public KeyPair getParChaves() {
        return parChavesBorda;
    }
    
    public int getTotalMensagensProcessadas() {
        return contadorMensagens.get();
    }
    
    private void log(String mensagem) {
        System.out.println("[RECEPTOR-UDP] " + mensagem);
    }
}

