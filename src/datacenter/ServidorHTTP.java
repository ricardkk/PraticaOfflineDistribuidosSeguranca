package datacenter;

import utils.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorHTTP implements Runnable {
    
    private int porta;
    private ServerSocket serverSocket;
    private ProcessadorRelatorios processador;
    private KeyPair parChavesDatacenter;
    private BancoDadosDatacenter bancoDados;
    private ExecutorService poolRequisicoes;
    private boolean ativo;
    private AtomicInteger contadorRequisicoes;
    private ConcurrentHashMap<String, PublicKey> chavesPublicasClientes;
    
    public ServidorHTTP(int porta, ProcessadorRelatorios processador, KeyPair parChavesDatacenter) {
        this(porta, processador, parChavesDatacenter, null);
    }
    
    public ServidorHTTP(int porta, ProcessadorRelatorios processador, KeyPair parChavesDatacenter, BancoDadosDatacenter bancoDados) {
        this.porta = porta;
        this.processador = processador;
        this.parChavesDatacenter = parChavesDatacenter;
        this.bancoDados = bancoDados;
        this.poolRequisicoes = Executors.newFixedThreadPool(10);
        this.ativo = false;
        this.contadorRequisicoes = new AtomicInteger(0);
        this.chavesPublicasClientes = new ConcurrentHashMap<>();
    }
    
    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(porta);
        ativo = true;
        new Thread(this, "ServidorHTTP").start();
        log(Cores.verde("Servidor HTTP iniciado na porta " + porta));
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
        poolRequisicoes.shutdown();
        log("Encerrado");
    }
    
    @Override
    public void run() {
        log("Aguardando requisições HTTP...");
        
        while (ativo) {
            try {
                Socket socket = serverSocket.accept();
                poolRequisicoes.execute(() -> processarRequisicao(socket));
                
            } catch (IOException e) {
                if (ativo) {
                    log("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }
    
    private void processarRequisicao(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream outputStream = socket.getOutputStream();
             PrintWriter out = new PrintWriter(outputStream, true)) {
            
            String linha = in.readLine();
            if (linha == null) return;
            
            contadorRequisicoes.incrementAndGet();
            
            String[] partes = linha.split(" ");
            if (partes.length < 2) return;
            
            String metodo = partes[0];
            String caminho = partes[1];
            
            log(Cores.azul("← Requisição: " + metodo + " " + caminho + 
                " de " + socket.getInetAddress().getHostAddress()));
            
            String tokenAuth = null;
            int contentLength = 0;
            String headerLine;
            
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Authorization: Bearer ")) {
                    tokenAuth = headerLine.substring("Authorization: Bearer ".length());
                } else if (headerLine.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(headerLine.substring("Content-Length: ".length()));
                }
            }
            
            if (metodo.equals("POST") && caminho.equals("/auth")) {
                processarAutenticacao(socket, in, outputStream, contentLength);
                return;
            }
            
            if (caminho.startsWith("/relatorio/")) {
                if (!TokenSessao.validarToken(tokenAuth)) {
                    log(Cores.vermelho("Token inválido ou ausente"));
                    enviarRespostaErro(out, 401, "Não autorizado. Token inválido ou ausente.");
                    return;
                }
                log(Cores.verde("Token JWT validado: " + TokenSessao.extrairClienteId(tokenAuth)));
            }
            
            String resposta;
            String contentType = "text/plain; charset=UTF-8";
            
            if (caminho.equals("/publickey")) {
                byte[] chavePublicaBytes = parChavesDatacenter.getPublic().getEncoded();
                resposta = Base64.getEncoder().encodeToString(chavePublicaBytes);
                log(Cores.azul("Chave pública fornecida para " + socket.getInetAddress().getHostAddress()));
                
            } else if (caminho.startsWith("/relatorio/")) {
                String tipo = caminho.substring("/relatorio/".length());
                resposta = gerarRelatorio(tipo);
                
            } else if (caminho.equals("/status")) {
                resposta = gerarStatus();
                
            } else if (caminho.startsWith("/admin/falha-replica/")) {
                // Endpoint para simular falha em réplica (para demonstração)
                resposta = processarFalhaReplica(caminho);
                
            } else if (caminho.startsWith("/admin/restaurar-replica/")) {
                // Endpoint para restaurar réplica
                resposta = processarRestaurarReplica(caminho);
                
            } else if (caminho.equals("/admin/status-replicas")) {
                // Endpoint para ver status das réplicas
                resposta = gerarStatusReplicas();
                
            } else {
                resposta = "404 - Recurso não encontrado\n\nEndpoints disponíveis:\n" +
                          "  POST /auth (autenticação)\n" +
                          "  GET /publickey\n" +
                          "  GET /relatorio/qualidade-ar\n" +
                          "  GET /relatorio/mapa-poluicao\n" +
                          "  GET /relatorio/ilhas-calor\n" +
                          "  GET /relatorio/risco-saude\n" +
                          "  GET /relatorio/conforto-ambiental\n" +
                          "  GET /status\n" +
                          "  GET /admin/falha-replica/{1-3}\n" +
                          "  GET /admin/restaurar-replica/{1-3}\n" +
                          "  GET /admin/status-replicas";
            }
            
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + resposta.getBytes("UTF-8").length);
            out.println("Connection: close");
            out.println();
            out.println(resposta);
            out.flush();
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao processar requisição: " + e.getMessage()));
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignora
            }
        }
    }
    
    private String gerarRelatorio(String tipo) {
        try {
            Relatorio relatorio = null;
            
            switch (tipo) {
                case "qualidade-ar":
                    relatorio = processador.gerarRelatorioQualidadeAr();
                    break;
                case "mapa-poluicao":
                    relatorio = processador.gerarMapaPoluicao();
                    break;
                case "ilhas-calor":
                    relatorio = processador.gerarAlertaIlhasCalor();
                    break;
                case "risco-saude":
                    relatorio = processador.gerarPrevisaoRiscoSaude();
                    break;
                case "conforto-ambiental":
                    relatorio = processador.gerarAnaliseConfortoAmbiental();
                    break;
                default:
                    return "Tipo de relatório desconhecido: " + tipo;
            }
            
            return relatorio.toString();
            
        } catch (Exception e) {
            return "Erro ao gerar relatório: " + e.getMessage();
        }
    }
    
    private String gerarStatus() {
        return "STATUS DO SISTEMA\n\n" +
               "Total de requisições atendidas: " + contadorRequisicoes.get() + "\n" +
               "Clientes autenticados: " + TokenSessao.getNumeroTokensAtivos() + "\n" +
               "Servidor HTTP: ATIVO\n" +
               "Porta: " + porta;
    }
    
    private String processarFalhaReplica(String caminho) {
        if (bancoDados == null) {
            return "ERRO: BancoDados não configurado para operações de réplica";
        }
        
        try {
            String idStr = caminho.substring("/admin/falha-replica/".length());
            int idReplica = Integer.parseInt(idStr);
            
            if (idReplica < 1 || idReplica > 3) {
                return "ERRO: ID da réplica deve ser 1, 2 ou 3";
            }
            
            bancoDados.simularFalhaReplica(idReplica);
            
            log(Cores.vermelho("ADMIN: Falha simulada na réplica " + idReplica));
            return "=== SIMULAÇÃO DE FALHA ===\n\n" +
                   "Réplica " + idReplica + " foi DESATIVADA.\n\n" +
                   "O sistema continua funcionando com as réplicas restantes.\n" +
                   "Use /admin/status-replicas para ver o estado atual.\n" +
                   "Use /admin/restaurar-replica/" + idReplica + " para reativar.";
            
        } catch (NumberFormatException e) {
            return "ERRO: ID da réplica inválido. Use 1, 2 ou 3.";
        }
    }
    
    private String processarRestaurarReplica(String caminho) {
        if (bancoDados == null) {
            return "ERRO: BancoDados não configurado para operações de réplica";
        }
        
        try {
            String idStr = caminho.substring("/admin/restaurar-replica/".length());
            int idReplica = Integer.parseInt(idStr);
            
            if (idReplica < 1 || idReplica > 3) {
                return "ERRO: ID da réplica deve ser 1, 2 ou 3";
            }
            
            bancoDados.restaurarReplica(idReplica);
            
            log(Cores.verde("ADMIN: Réplica " + idReplica + " restaurada"));
            return "=== RESTAURAÇÃO DE RÉPLICA ===\n\n" +
                   "Réplica " + idReplica + " foi REATIVADA.\n\n" +
                   "Use /admin/status-replicas para ver o estado atual.";
            
        } catch (NumberFormatException e) {
            return "ERRO: ID da réplica inválido. Use 1, 2 ou 3.";
        }
    }
    
    private String gerarStatusReplicas() {
        if (bancoDados == null) {
            return "ERRO: BancoDados não configurado";
        }
        
        return bancoDados.getStatusReplicas();
    }
    
    private void processarAutenticacao(Socket socket, BufferedReader in, OutputStream out, int contentLength) {
        try {
            log(Cores.ciano("Processando autenticação..."));
            
            byte[] dadosCriptografados = new byte[contentLength];
            
            int totalLido = 0;
            for (int i = 0; i < contentLength; i++) {
                int c = in.read();
                if (c == -1) {
                    break;
                }
                dadosCriptografados[i] = (byte) c;
                totalLido++;
            }
            
            if (totalLido != contentLength) {
                log(Cores.vermelho("Dados incompletos"));
                enviarRespostaErro(out, 400, "Dados incompletos");
                return;
            }
            
            byte[] dadosReais;
            try {
                dadosReais = Base64.getDecoder().decode(dadosCriptografados);
            } catch (IllegalArgumentException e) {
                log(Cores.vermelho("Corpo da requisição não é Base64 válido"));
                enviarRespostaErro(out, 400, "Bad Request - Invalid Base64");
                return;
            }
            
            log(Cores.magenta("Mensagem criptografada recebida: " + 
                bytesParaHex(dadosReais, 32)));
            
            byte[] dadosDescriptografados = ServicoSeguranca.descriptografar(
                dadosReais, 
                parChavesDatacenter.getPrivate()
            );
            
            Mensagem mensagem = Mensagem.desserializar(dadosDescriptografados);
            
            if (mensagem.getTipo() != Mensagem.Tipo.AUTENTICACAO) {
                enviarRespostaErro(out, 400, "Tipo de mensagem inválido");
                return;
            }
            
            Credenciais credenciais = (Credenciais) mensagem.getConteudo();
            String clienteId = credenciais.getId();
            String clienteNome = credenciais.getNome();
            
            byte[] chavePublicaBytes = credenciais.getChavePublicaBytes();
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(chavePublicaBytes);
            PublicKey chavePublicaCliente = keyFactory.generatePublic(keySpec);
            
            log(Cores.verde("Cliente autenticado: " + clienteId + " (" + clienteNome + ")"));
            
            chavesPublicasClientes.put(clienteId, chavePublicaCliente);
            
            String token = TokenSessao.gerarToken(clienteId, clienteNome);
            
            Mensagem resposta = new Mensagem(Mensagem.Tipo.ACK, "DATACENTER", token);
            byte[] respostaSerializada = resposta.serializar();
            
            byte[] respostaCriptografada = ServicoSeguranca.criptografar(
                respostaSerializada, 
                chavePublicaCliente
            );
            
            log(Cores.magenta("Mensagem criptografada: " + 
                bytesParaHex(respostaCriptografada, 32)));
            
            String respostaBase64 = Base64.getEncoder().encodeToString(respostaCriptografada);
            
            log(Cores.magenta("Mensagem criptografada: " + 
                bytesParaHex(respostaCriptografada, 32)));
            
            PrintWriter writer = new PrintWriter(out, false);
            writer.print("HTTP/1.1 200 OK\r\n");
            writer.print("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.print("Content-Length: " + respostaBase64.length() + "\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.print(respostaBase64);
            writer.flush();
            
            log(Cores.verde("Token JWT gerado e enviado para " + clienteId));
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro na autenticação: " + e.getMessage()));
            e.printStackTrace();
            enviarRespostaErro(out, 500, "Erro ao processar autenticação");
        }
    }
    
    private void enviarRespostaErro(OutputStream out, int codigo, String mensagem) {
        try {
            PrintWriter writer = new PrintWriter(out, true);
            writer.println("HTTP/1.1 " + codigo + " Error");
            writer.println("Content-Type: text/plain");
            writer.println("Content-Length: " + mensagem.length());
            writer.println("Connection: close");
            writer.println();
            writer.print(mensagem);
            writer.flush();
        } catch (Exception e) {
        }
    }
    
    private void enviarRespostaErro(PrintWriter out, int codigo, String mensagem) {
        out.println("HTTP/1.1 " + codigo + " Error");
        out.println("Content-Type: text/plain");
        out.println("Content-Length: " + mensagem.length());
        out.println("Connection: close");
        out.println();
        out.print(mensagem);
        out.flush();
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
    
    public int getTotalRequisicoes() {
        return contadorRequisicoes.get();
    }
    
    private void log(String mensagem) {
        System.out.println("[HTTP] " + mensagem);
    }
}

