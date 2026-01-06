package cliente;

import utils.*;
import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

public class ClienteConsulta implements Runnable {
    
    private String id;
    private String nome;
    private KeyPair parChaves;
    private String hostDatacenter;
    private int portaHTTP;
    private ClienteHTTP clienteHTTP;
    private PublicKey chavePublicaDatacenter;
    private boolean autenticado;
    private String tokenJWT;
    private String tag;
    
    public ClienteConsulta(String id, String nome, String hostDatacenter, int portaHTTP) throws Exception {
        this.id = id;
        this.nome = nome;
        this.hostDatacenter = hostDatacenter;
        this.portaHTTP = portaHTTP;
        this.parChaves = ServicoSeguranca.gerarParChavesRSA();
        this.clienteHTTP = new ClienteHTTP(hostDatacenter, portaHTTP);
        this.autenticado = false;
        this.tag = "[CLIENTE-" + id + "]";
    }
    
    public boolean descobrirEConectar() {
        try {
            log("Consultando Discovery Service...");
            
            ServicoDescoberta.ServicoInfo datacenter = ServicoDescoberta.getInstancia().descobrirDatacenter();
            
            if (datacenter == null) {
                log(Cores.vermelho("Nenhum datacenter disponivel no Discovery Service"));
                return false;
            }
            
            log("Datacenter descoberto: " + datacenter.getNome() + " em " + datacenter.getEnderecoCompleto());
            
            this.hostDatacenter = datacenter.getHost();
            this.portaHTTP = datacenter.getPorta();
            this.clienteHTTP = new ClienteHTTP(hostDatacenter, portaHTTP);
            
            ServicoDescoberta.getInstancia().registrarServico(
                id,
                nome,
                ServicoDescoberta.TipoServico.CLIENTE,
                "localhost",
                0,
                "Cliente consumidor de relatórios"
            );
            
            return conectar();
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro na descoberta: " + e.getMessage()));
            return false;
        }
    }
    
    public boolean conectar() {
        try {
            log("Conectando ao datacenter em " + hostDatacenter + ":" + portaHTTP + "...");
            
            if (!clienteHTTP.testarConexao()) {
                log(Cores.vermelho("Falha ao conectar ao datacenter"));
                return false;
            }
            
            log(Cores.ciano("Iniciando autenticação segura..."));
            return autenticar();
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao conectar: " + e.getMessage()));
            return false;
        }
    }
    
    private boolean autenticar() {
        try {
            log("Obtendo chave pública do datacenter...");
            
            chavePublicaDatacenter = obterChavePublicaDatacenter();
            
            if (chavePublicaDatacenter == null) {
                log(Cores.vermelho("Não foi possível obter chave pública do datacenter"));
                return false;
            }
            
            Credenciais credenciais = new Credenciais(id, nome, parChaves.getPublic());
            Mensagem mensagem = new Mensagem(Mensagem.Tipo.AUTENTICACAO, id, credenciais);
            
            byte[] mensagemSerializada = mensagem.serializar();
            byte[] mensagemCriptografada = ServicoSeguranca.criptografar(
                mensagemSerializada, 
                chavePublicaDatacenter
            );
            
            log(Cores.magenta("Mensagem criptografada: " + Cores.bytesParaHex(mensagemCriptografada, 32)));
            log("Enviando credenciais para datacenter...");
            
            byte[] mensagemBase64 = Base64.getEncoder().encode(mensagemCriptografada);
            byte[] respostaCriptografada = clienteHTTP.requisicaoPOST("/auth", mensagemBase64);
            
            if (respostaCriptografada == null || respostaCriptografada.length == 0) {
                log(Cores.vermelho("Resposta vazia do datacenter"));
                return false;
            }
            
            log("Resposta recebida, descriptografando...");
            
            byte[] respostaDescriptografada = ServicoSeguranca.descriptografar(
                respostaCriptografada, 
                parChaves.getPrivate()
            );
            
            Mensagem resposta = Mensagem.desserializar(respostaDescriptografada);
            
            if (resposta.getTipo() == Mensagem.Tipo.ACK) {
                tokenJWT = (String) resposta.getConteudo();
                clienteHTTP.setTokenAutenticacao(tokenJWT);
                autenticado = true;
                log(Cores.verde("Autenticação bem-sucedida!"));
                log(Cores.verde("Token JWT recebido e configurado"));
                return true;
            } else {
                log(Cores.vermelho("Autenticação falhou: " + resposta.getConteudo()));
                return false;
            }
            
        } catch (Exception e) {
            log(Cores.vermelho("ERRO na autenticação: " + e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }
    
    private PublicKey obterChavePublicaDatacenter() {
        try {
            log("Solicitando chave pública do datacenter...");
            
            ClienteHTTP clienteTemp = new ClienteHTTP(hostDatacenter, portaHTTP);
            String chaveBase64 = clienteTemp.requisicaoGET("/publickey");
            
            if (chaveBase64 == null || chaveBase64.trim().isEmpty()) {
                log(Cores.vermelho("Chave pública não recebida"));
                return null;
            }
            
            byte[] chaveBytes = Base64.getDecoder().decode(chaveBase64.trim());
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(chaveBytes);
            PublicKey chavePublica = keyFactory.generatePublic(keySpec);
            
            log(Cores.verde("Chave pública do datacenter obtida com sucesso"));
            return chavePublica;
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao obter chave pública: " + e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }
    
    public String consultarRelatorio(String tipoRelatorio) {
        if (!autenticado) {
            log(Cores.vermelho("Cliente não conectado ao datacenter"));
            return null;
        }
        
        try {
            log("Solicitando relatório: " + tipoRelatorio);
            String caminho = "/relatorio/" + tipoRelatorio;
            String resposta = clienteHTTP.requisicaoGET(caminho);
            log(Cores.verde("Relatorio recebido"));
            return resposta;
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao consultar relatorio: " + e.getMessage()));
            return null;
        }
    }
    
    public String consultarStatus() {
        if (!autenticado) {
            log(Cores.vermelho("Cliente não conectado ao datacenter"));
            return null;
        }
        
        try {
            log("Consultando status do sistema...");
            String resposta = clienteHTTP.requisicaoGET("/status");
            log(Cores.verde("Status recebido"));
            return resposta;
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro ao consultar status: " + e.getMessage()));
            return null;
        }
    }
    
    public void analisarQualidadeAr() {
        log(Cores.ciano("\n=== ANÁLISE: QUALIDADE DO AR ==="));
        String relatorio = consultarRelatorio("qualidade-ar");
        
        if (relatorio != null) {
            System.out.println(relatorio);
            tomarDecisaoQualidadeAr(relatorio);
        }
    }
    
    public void analisarMapaPoluicao() {
        log(Cores.ciano("\n=== ANÁLISE: MAPA DE POLUIÇÃO ==="));
        String relatorio = consultarRelatorio("mapa-poluicao");
        
        if (relatorio != null) {
            System.out.println(relatorio);
            tomarDecisaoMapaPoluicao(relatorio);
        }
    }
    
    public void analisarIlhasCalor() {
        log(Cores.ciano("\n=== ANÁLISE: ILHAS DE CALOR ==="));
        String relatorio = consultarRelatorio("ilhas-calor");
        
        if (relatorio != null) {
            System.out.println(relatorio);
            tomarDecisaoIlhasCalor(relatorio);
        }
    }
    
    public void analisarRiscoSaude() {
        log(Cores.ciano("\n=== ANÁLISE: RISCO À SAÚDE ==="));
        String relatorio = consultarRelatorio("risco-saude");
        
        if (relatorio != null) {
            System.out.println(relatorio);
            tomarDecisaoRiscoSaude(relatorio);
        }
    }
    
    public void analisarConfortoAmbiental() {
        log(Cores.ciano("\n=== ANÁLISE: CONFORTO AMBIENTAL ==="));
        String relatorio = consultarRelatorio("conforto-ambiental");
        
        if (relatorio != null) {
            System.out.println(relatorio);
            tomarDecisaoConfortoAmbiental(relatorio);
        }
    }
    
    private void tomarDecisaoQualidadeAr(String relatorio) {
        log(Cores.amarelo("\n>>> RECOMENDACOES ACADEMICAS BASEADAS NA QUALIDADE DO AR:"));
        
        List<String> decisoes = new ArrayList<>();
        
        if (relatorio.contains("Crítica") || relatorio.contains("Péssima")) {
            decisoes.add("ALERTA MAXIMO: Suspender todas as atividades ao ar livre no campus");
            decisoes.add("Cancelar aulas praticas de campo e laboratorios externos");
            decisoes.add("Suspender atividades esportivas e educacao fisica");
            decisoes.add("Recomendar uso de mascaras para transito entre predios");
            decisoes.add("Ativar sistema de ventilacao e ar condicionado em salas de aula");
        } else if (relatorio.contains("Ruim")) {
            decisoes.add("Limitar atividades ao ar livre a periodos curtos");
            decisoes.add("Transferir aulas praticas externas para ambientes fechados");
            decisoes.add("Reduzir intensidade de atividades fisicas");
            decisoes.add("Alertar estudantes com problemas respiratorios");
        } else if (relatorio.contains("Moderada")) {
            decisoes.add("Monitorar condicoes antes de aulas praticas externas");
            decisoes.add("Manter janelas fechadas em salas proximas a vias movimentadas");
            decisoes.add("Programar atividades ao ar livre para horarios de melhor qualidade");
        } else {
            decisoes.add("Condicoes adequadas para todas as atividades academicas");
            decisoes.add("Aproveitar para realizar atividades praticas de campo");
        }
        
        for (String decisao : decisoes) {
            log("  - " + decisao);
        }
    }
    
    private void tomarDecisaoMapaPoluicao(String relatorio) {
        log(Cores.amarelo("\n>>> PLANEJAMENTO BASEADO NO MAPA DE POLUICAO:"));
        
        List<String> decisoes = new ArrayList<>();
        decisoes.add("Identificar areas criticas para evitar construcao de novos predios");
        decisoes.add("Planejar localizacao de laboratorios sensiveis em areas menos poluidas");
        decisoes.add("Priorizar arborizacao nas regioes mais afetadas do campus");
        decisoes.add("Criar zonas de circulacao preferencial para pedestres e bicicletas");
        decisoes.add("Instalar mais sensores em pontos criticos identificados");
        decisoes.add("Ajustar rotas de onibus universitarios para evitar areas poluidas");
        
        for (String decisao : decisoes) {
            log("  - " + decisao);
        }
    }
    
    private void tomarDecisaoIlhasCalor(String relatorio) {
        log(Cores.amarelo("\n>>> MEDIDAS BASEADAS EM ILHAS DE CALOR:"));
        
        List<String> decisoes = new ArrayList<>();
        
        if (relatorio.contains("SEVERA")) {
            decisoes.add("URGENTE: Ativar ar condicionado em todos os predios academicos");
            decisoes.add("Disponibilizar bebedouros adicionais em pontos estrategicos");
            decisoes.add("Ajustar horarios de aulas para evitar picos de calor");
            decisoes.add("Liberar uso de vestuario mais leve em atividades academicas");
        }
        
        decisoes.add("Expandir areas sombreadas com arvores e coberturas no campus");
        decisoes.add("Instalar toldos e coberturas em areas de circulacao");
        decisoes.add("Priorizar construcao de predios com ventilacao natural");
        decisoes.add("Pintar telhados e paredes externas com cores claras");
        decisoes.add("Criar espacos de descanso climatizados entre aulas");
        
        for (String decisao : decisoes) {
            log("  - " + decisao);
        }
    }
    
    private void tomarDecisaoRiscoSaude(String relatorio) {
        log(Cores.amarelo("\n>>> ACOES BASEADAS NO RISCO A SAUDE:"));
        
        List<String> decisoes = new ArrayList<>();
        
        if (relatorio.contains("CRÍTICO")) {
            decisoes.add("ALERTA CRITICO: Acionar servico de saude do campus");
            decisoes.add("Disponibilizar mascaras em todas as entradas do campus");
            decisoes.add("Enviar comunicado via e-mail e SMS para toda comunidade academica");
            decisoes.add("Suspender aulas e transferir para modo remoto se necessario");
            decisoes.add("Mobilizar enfermaria para atendimentos preventivos");
        } else if (relatorio.contains("ALTO")) {
            decisoes.add("Alertar estudantes e servidores com condicoes respiratorias");
            decisoes.add("Recomendar uso de mascaras para grupos de risco");
            decisoes.add("Suspender atividades fisicas intensas");
            decisoes.add("Manter ambientes fechados bem ventilados");
        }
        
        decisoes.add("Divulgar boletim diario de qualidade ambiental no portal");
        decisoes.add("Reforcar campanhas de prevencao e cuidados com a saude");
        decisoes.add("Disponibilizar atendimento medico de plantao");
        
        for (String decisao : decisoes) {
            log("  - " + decisao);
        }
    }
    
    private void tomarDecisaoConfortoAmbiental(String relatorio) {
        log(Cores.amarelo("\n>>> MELHORIAS BASEADAS NO CONFORTO AMBIENTAL:"));
        
        List<String> decisoes = new ArrayList<>();
        
        if (relatorio.contains("Ruim")) {
            decisoes.add("Implementar isolamento acustico em salas proximas a fontes de ruido");
            decisoes.add("Criar zonas de silencio para estudo e concentracao");
            decisoes.add("Fiscalizar obras e atividades ruidosas no campus");
            decisoes.add("Ajustar climatizacao em ambientes desconfortaveis");
        }
        
        decisoes.add("Criar mais espacos de convivencia ao ar livre no campus");
        decisoes.add("Instalar ventiladores e sistemas de resfriamento em areas abertas");
        decisoes.add("Programar horarios de estudo em ambientes mais confortaveis");
        decisoes.add("Melhorar isolamento termico de salas de aula");
        decisoes.add("Usar dados para planejar novos espacos academicos");
        
        for (String decisao : decisoes) {
            log("  - " + decisao);
        }
    }
    
    @Override
    public void run() {
        if (!descobrirEConectar()) {
            log(Cores.vermelho("Falha na conexão. Encerrando cliente."));
            return;
        }
        
        log(Cores.ciano("\n" + "=".repeat(80)));
        log(Cores.ciano("INICIANDO CONSULTAS E ANÁLISES"));
        log(Cores.ciano("=".repeat(80)));
        
        try {
            analisarQualidadeAr();
            Thread.sleep(1000);
            
            analisarRiscoSaude();
            Thread.sleep(1000);
            
            log(Cores.verde("\nConsultas concluidas com sucesso!"));
            
        } catch (Exception e) {
            log(Cores.vermelho("Erro durante consultas: " + e.getMessage()));
        } finally {
            ServicoDescoberta.getInstancia().removerServico(id);
        }
    }
    
    public String getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAutenticado() { return autenticado; }
    
    private void log(String mensagem) {
        System.out.println(tag + " " + mensagem);
    }
}

