package datacenter;

import utils.Cores;
import utils.ServicoSeguranca;
import utils.ServicoDescoberta;
import java.security.KeyPair;

public class ServidorDatacenter {
    
    private static final int PORTA_TCP = 6000;
    private static final int PORTA_HTTP = 8080;
    
    private KeyPair parChavesDatacenter;
    private BancoDadosDatacenter bancoDados;
    private ProcessadorRelatorios processador;
    private ReceptorTCP receptorTCP;
    private ServidorHTTP servidorHTTP;
    private boolean ativo;
    
    public ServidorDatacenter() throws Exception {
        log("Inicializando Servidor Datacenter...");
        
        log("Gerando par de chaves RSA...");
        this.parChavesDatacenter = ServicoSeguranca.gerarParChavesRSA();
        
        log("Inicializando banco de dados...");
        this.bancoDados = new BancoDadosDatacenter();
        
        log("Inicializando processador de relatórios...");
        this.processador = new ProcessadorRelatorios(bancoDados);
        
        log("Inicializando receptor TCP...");
        this.receptorTCP = new ReceptorTCP(PORTA_TCP, parChavesDatacenter, bancoDados);
        
        log("Inicializando servidor HTTP...");
        this.servidorHTTP = new ServidorHTTP(PORTA_HTTP, processador, parChavesDatacenter, bancoDados);
        
        this.ativo = false;
    }
    
    public void iniciar() throws Exception {
        if (ativo) {
            log("Servidor já está em execução!");
            return;
        }
        
        log("=".repeat(60));
        log(Cores.ciano("INICIANDO SERVIDOR DATACENTER"));
        log("=".repeat(60));
        
        receptorTCP.iniciar();
        servidorHTTP.iniciar();
        
        ativo = true;
        
        ServicoDescoberta.getInstancia().registrarServico(
            "DATACENTER-MAIN",
            "Datacenter Principal",
            ServicoDescoberta.TipoServico.DATACENTER,
            "localhost",
            PORTA_HTTP,
            "Servidor central de análise e relatórios"
        );
        
        log(Cores.verde("Servidor Datacenter iniciado com sucesso!"));
        log("  - Porta TCP (borda): " + PORTA_TCP);
        log("  - Porta HTTP (clientes): " + PORTA_HTTP);
        log("  - Chave pública disponível para a borda");
        log("  - Registrado no Discovery Service");
        log("  - Aguardando dados e requisições...");
    }
    
    public void parar() {
        if (!ativo) return;
        
        log("Encerrando Servidor Datacenter...");
        
        receptorTCP.parar();
        servidorHTTP.parar();
        
        ServicoDescoberta.getInstancia().removerServico("DATACENTER-MAIN");
        
        ativo = false;
        
        exibirEstatisticas();
        log("Servidor Datacenter encerrado.");
    }
    
    public void exibirEstatisticas() {
        log("=".repeat(60));
        log("ESTATÍSTICAS DO DATACENTER");
        log("=".repeat(60));
        log("Total de leituras recebidas: " + bancoDados.getTotalLeiturasRecebidas());
        log("Total de leituras em memória: " + bancoDados.getTotalLeituras());
        log("Total de mensagens TCP processadas: " + receptorTCP.getTotalMensagensProcessadas());
        log("Total de requisições HTTP atendidas: " + servidorHTTP.getTotalRequisicoes());
        log("=".repeat(60));
    }
    
    public void gerarRelatorios() {
        log(Cores.amarelo("\n>>> Gerando relatórios..."));
        
        Relatorio r1 = processador.gerarRelatorioQualidadeAr();
        bancoDados.salvarRelatorio(r1);
        System.out.println(r1);
        
        Relatorio r2 = processador.gerarMapaPoluicao();
        bancoDados.salvarRelatorio(r2);
        System.out.println(r2);
        
        Relatorio r3 = processador.gerarAlertaIlhasCalor();
        bancoDados.salvarRelatorio(r3);
        System.out.println(r3);
        
        Relatorio r4 = processador.gerarPrevisaoRiscoSaude();
        bancoDados.salvarRelatorio(r4);
        System.out.println(r4);
        
        Relatorio r5 = processador.gerarAnaliseConfortoAmbiental();
        bancoDados.salvarRelatorio(r5);
        System.out.println(r5);
        
        log(Cores.verde("Todos os relatorios foram gerados e salvos!"));
    }
    
    public byte[] getChavePublicaBytes() {
        return parChavesDatacenter.getPublic().getEncoded();
    }
    
    public KeyPair getParChaves() {
        return parChavesDatacenter;
    }
    
    public boolean isAtivo() {
        return ativo;
    }
    
    public BancoDadosDatacenter getBancoDados() {
        return bancoDados;
    }
    
    private void log(String mensagem) {
        System.out.println("[DATACENTER] " + mensagem);
    }
    
    public static void main(String[] args) {
        try {
            ServidorDatacenter servidor = new ServidorDatacenter();
            servidor.iniciar();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidor.parar();
            }));
            
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println("ERRO ao iniciar Servidor Datacenter: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

